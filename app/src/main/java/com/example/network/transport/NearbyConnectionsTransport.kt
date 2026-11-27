package com.example.network.transport

import android.content.Context
import com.example.data.local.TransportKind
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NearbyConnectionsTransport(private val context: Context) {

    companion object {
        private const val SERVICE_ID = "com.aistudio.offlinemessenger.mesh"
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }

    private val _status = MutableStateFlow(checkAvailability())
    val status: StateFlow<TransportStatusSnapshot> = _status.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<PendingConnectionRequest>>(emptyList())
    val pendingRequests: StateFlow<List<PendingConnectionRequest>> = _pendingRequests.asStateFlow()

    private val _connectedEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedEndpoints: StateFlow<Map<String, String>> = _connectedEndpoints.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<String, ByteArray>> = _incomingBytes.asSharedFlow()

    private val _disconnectedEndpoint = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val disconnectedEndpoint: SharedFlow<String> = _disconnectedEndpoint.asSharedFlow()

    private val pendingEndpointNames = mutableMapOf<String, String>()

    fun checkAvailability(): TransportStatusSnapshot {
        val gmsResult = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)

        val supported = gmsResult == ConnectionResult.SUCCESS
        return TransportStatusSnapshot(
            kind = TransportKind.NEARBY_CONNECTIONS,
            isSupported = supported,
            availabilityNote = if (supported) {
                "Google Nearby Connections ready (BLE + Wi-Fi Direct / Hotspot)"
            } else {
                "Google Play Services unavailable on this device. Use Wi-Fi / Hotspot or Bluetooth fallback."
            }
        )
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    _incomingBytes.tryEmit(endpointId to bytes)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pendingEndpointNames[endpointId] = info.endpointName
            _pendingRequests.update { list ->
                list.filterNot { it.endpointId == endpointId } + PendingConnectionRequest(
                    endpointId = endpointId,
                    peerName = info.endpointName,
                    transportKind = TransportKind.NEARBY_CONNECTIONS,
                    authDigits = info.authenticationDigits
                )
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            _pendingRequests.update { list -> list.filterNot { it.endpointId == endpointId } }
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val name = pendingEndpointNames.remove(endpointId) ?: "Nearby Peer"
                    _connectedEndpoints.update { it + (endpointId to name) }
                }
                else -> {
                    pendingEndpointNames.remove(endpointId)
                    _status.update {
                        it.copy(errorMessage = "Nearby connection failed (code ${result.status.statusCode})")
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            pendingEndpointNames.remove(endpointId)
            _connectedEndpoints.update { it - endpointId }
            _disconnectedEndpoint.tryEmit(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _discoveredPeers.update { list ->
                list.filterNot { it.id == endpointId } + DiscoveredPeer(
                    id = endpointId,
                    name = info.endpointName,
                    transportKind = TransportKind.NEARBY_CONNECTIONS,
                    addressLabel = "Nearby Endpoint • ${info.serviceId.takeLast(8)}"
                )
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredPeers.update { list -> list.filterNot { it.id == endpointId } }
        }
    }

    fun startAdvertising(localName: String) {
        if (!checkAvailability().isSupported) {
            _status.update { it.copy(status = RadioStatus.ERROR, errorMessage = "Play Services unavailable") }
            return
        }
        try {
            val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            client.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener {
                    _status.update {
                        it.copy(status = RadioStatus.ADVERTISING, errorMessage = null, localEndpointInfo = localName)
                    }
                }
                .addOnFailureListener { e ->
                    _status.update {
                        it.copy(status = RadioStatus.ERROR, errorMessage = e.localizedMessage ?: "Advertising failed")
                    }
                }
        } catch (se: SecurityException) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Missing Nearby permission: ${se.message}")
            }
        }
    }

    fun startDiscovery() {
        if (!checkAvailability().isSupported) {
            _status.update { it.copy(status = RadioStatus.ERROR, errorMessage = "Play Services unavailable") }
            return
        }
        try {
            _discoveredPeers.value = emptyList()
            val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            client.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnSuccessListener {
                    _status.update { it.copy(status = RadioStatus.DISCOVERING, errorMessage = null) }
                }
                .addOnFailureListener { e ->
                    _status.update {
                        it.copy(status = RadioStatus.ERROR, errorMessage = e.localizedMessage ?: "Discovery failed")
                    }
                }
        } catch (se: SecurityException) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Missing Nearby permission: ${se.message}")
            }
        }
    }

    fun stopAllRadio() {
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
        }
        _status.update { it.copy(status = RadioStatus.IDLE, errorMessage = null) }
    }

    fun requestConnection(localName: String, endpointId: String) {
        try {
            _status.update { it.copy(status = RadioStatus.CONNECTING) }
            client.requestConnection(localName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    _status.update {
                        it.copy(status = RadioStatus.ERROR, errorMessage = e.localizedMessage ?: "Request failed")
                    }
                }
        } catch (se: SecurityException) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Permission denied: ${se.message}")
            }
        }
    }

    fun acceptConnection(endpointId: String) {
        try {
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    _status.update { it.copy(errorMessage = e.localizedMessage) }
                }
        } catch (se: SecurityException) {
            _status.update { it.copy(errorMessage = se.message) }
        }
    }

    fun rejectConnection(endpointId: String) {
        runCatching { client.rejectConnection(endpointId) }
        _pendingRequests.update { list -> list.filterNot { it.endpointId == endpointId } }
    }

    fun sendBytes(endpointId: String, bytes: ByteArray): Boolean {
        return try {
            client.sendPayload(endpointId, Payload.fromBytes(bytes))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disconnect(endpointId: String) {
        runCatching { client.disconnectFromEndpoint(endpointId) }
        _connectedEndpoints.update { it - endpointId }
    }
}
