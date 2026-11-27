package com.example.network.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.example.core.security.PacketValidator
import com.example.data.local.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothClassicTransport(private val context: Context) {

    companion object {
        private val SERVICE_UUID: UUID = UUID.fromString("c4f8a210-6b3e-4e92-9f41-2d8a7e5c31b0")
        private const val SERVICE_NAME = "OfflineMessengerRFCOMM"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        mgr?.adapter
    }

    private val _status = MutableStateFlow(checkAvailability())
    val status: StateFlow<TransportStatusSnapshot> = _status.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _pairedPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val pairedPeers: StateFlow<List<DiscoveredPeer>> = _pairedPeers.asStateFlow()

    private val _connectedEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedEndpoints: StateFlow<Map<String, String>> = _connectedEndpoints.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<String, ByteArray>> = _incomingBytes.asSharedFlow()

    private val _disconnectedEndpoint = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val disconnectedEndpoint: SharedFlow<String> = _disconnectedEndpoint.asSharedFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var receiverRegistered = false

    private val activeSockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val outputStreams = ConcurrentHashMap<String, DataOutputStream>()

    fun checkAvailability(): TransportStatusSnapshot {
        val hasHw = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val adapter = bluetoothAdapter
        val note = when {
            !hasHw || adapter == null -> "Bluetooth hardware is not available on this device."
            !adapter.isEnabled -> "Bluetooth is turned off. Enable Bluetooth in system settings."
            else -> "Bluetooth Classic RFCOMM ready (~10m range, lower file throughput)."
        }
        return TransportStatusSnapshot(
            kind = TransportKind.BLUETOOTH_CLASSIC,
            isSupported = hasHw && adapter != null,
            availabilityNote = note
        )
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val addr = device.address ?: return
                    val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: addr
                    val epId = "bt:$addr"
                    _discoveredPeers.update { list ->
                        list.filterNot { it.id == epId } + DiscoveredPeer(
                            id = epId,
                            name = name,
                            transportKind = TransportKind.BLUETOOTH_CLASSIC,
                            addressLabel = addr
                        )
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_status.value.status == RadioStatus.DISCOVERING) {
                        _status.update { it.copy(status = RadioStatus.IDLE) }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        try {
            val bonded = adapter.bondedDevices.orEmpty().map { dev ->
                val addr = dev.address ?: ""
                val name = dev.name?.takeIf { it.isNotBlank() } ?: addr
                DiscoveredPeer(
                    id = "bt:$addr",
                    name = name,
                    transportKind = TransportKind.BLUETOOTH_CLASSIC,
                    addressLabel = addr,
                    isPaired = true
                )
            }
            _pairedPeers.value = bonded
        } catch (se: SecurityException) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Bluetooth permission required: ${se.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRfcommServer() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Enable Bluetooth before starting server")
            }
            return
        }
        stopRfcommServer()
        scope.launch {
            try {
                val srv = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                serverSocket = srv
                _status.update {
                    it.copy(status = RadioStatus.ACTIVE_SERVER, errorMessage = null)
                }
                acceptJob = launch {
                    while (isActive) {
                        val sock = runCatching { srv.accept() }.getOrNull() ?: break
                        val addr = sock.remoteDevice?.address ?: "unknown"
                        val name = runCatching { sock.remoteDevice?.name }.getOrNull() ?: addr
                        handleSocket("bt:$addr", name, sock)
                    }
                }
            } catch (se: SecurityException) {
                _status.update {
                    it.copy(status = RadioStatus.ERROR, errorMessage = "Missing Bluetooth Connect permission")
                }
            } catch (e: Exception) {
                _status.update {
                    it.copy(status = RadioStatus.ERROR, errorMessage = "Bluetooth server error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun stopRfcommServer() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (_status.value.status == RadioStatus.ACTIVE_SERVER) {
            _status.update { it.copy(status = RadioStatus.IDLE) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Enable Bluetooth to scan devices")
            }
            return
        }
        try {
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(discoveryReceiver, filter)
                }
                receiverRegistered = true
            }
            _discoveredPeers.value = emptyList()
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
            _status.update { it.copy(status = RadioStatus.DISCOVERING, errorMessage = null) }
            refreshPairedDevices()
        } catch (se: SecurityException) {
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "Bluetooth Scan permission required")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        runCatching { bluetoothAdapter?.cancelDiscovery() }
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
        if (_status.value.status == RadioStatus.DISCOVERING) {
            _status.update { it.copy(status = RadioStatus.IDLE) }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(macAddress: String) {
        val adapter = bluetoothAdapter ?: return
        val cleanMac = macAddress.removePrefix("bt:")
        scope.launch {
            _status.update { it.copy(status = RadioStatus.CONNECTING, errorMessage = null) }
            try {
                runCatching { adapter.cancelDiscovery() }
                val device = adapter.getRemoteDevice(cleanMac)
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                val name = runCatching { device.name }.getOrNull() ?: cleanMac
                handleSocket("bt:$cleanMac", name, socket)
                _status.update { it.copy(status = RadioStatus.IDLE, errorMessage = null) }
            } catch (se: SecurityException) {
                _status.update {
                    it.copy(status = RadioStatus.ERROR, errorMessage = "Bluetooth Connect permission denied")
                }
            } catch (e: Exception) {
                _status.update {
                    it.copy(
                        status = RadioStatus.ERROR,
                        errorMessage = "Bluetooth connect failed: ${e.localizedMessage ?: "Ensure peer started RFCOMM server"}"
                    )
                }
            }
        }
    }

    private fun handleSocket(endpointId: String, peerName: String, socket: BluetoothSocket) {
        activeSockets[endpointId] = socket
        outputStreams[endpointId] = DataOutputStream(socket.outputStream)
        _connectedEndpoints.update { it + (endpointId to peerName) }

        scope.launch {
            try {
                val input = DataInputStream(socket.inputStream)
                while (isActive && socket.isConnected) {
                    val length = input.readInt()
                    if (length <= 0 || length > PacketValidator.MAX_FRAME_BYTES) break
                    val buffer = ByteArray(length)
                    input.readFully(buffer)
                    _incomingBytes.tryEmit(endpointId to buffer)
                }
            } catch (_: Exception) {
                // Disconnected
            } finally {
                disconnect(endpointId)
            }
        }
    }

    fun sendBytes(endpointId: String, bytes: ByteArray): Boolean {
        if (!PacketValidator.validateFrameSize(bytes)) return false
        val out = outputStreams[endpointId] ?: return false
        return try {
            synchronized(out) {
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
            true
        } catch (_: Exception) {
            disconnect(endpointId)
            false
        }
    }

    fun disconnect(endpointId: String) {
        outputStreams.remove(endpointId)
        val sock = activeSockets.remove(endpointId)
        runCatching { sock?.close() }
        val existed = _connectedEndpoints.value.containsKey(endpointId)
        _connectedEndpoints.update { it - endpointId }
        if (existed) _disconnectedEndpoint.tryEmit(endpointId)
    }
}
