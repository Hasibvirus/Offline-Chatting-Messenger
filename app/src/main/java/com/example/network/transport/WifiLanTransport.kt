package com.example.network.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class WifiLanTransport(private val context: Context) {

    companion object {
        const val DEFAULT_PORT = 48219
        private const val NSD_SERVICE_TYPE = "_offlinemsg._tcp."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val _status = MutableStateFlow(
        TransportStatusSnapshot(
            kind = TransportKind.WIFI_LAN_HOTSPOT,
            isSupported = true,
            availabilityNote = "Local Wi-Fi & Mobile Hotspot TCP/NSD ready (No Internet needed)",
            localEndpointInfo = "${getLocalIpAddresses().firstOrNull() ?: "Unavailable"}:$DEFAULT_PORT"
        )
    )
    val status: StateFlow<TransportStatusSnapshot> = _status.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _connectedEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedEndpoints: StateFlow<Map<String, String>> = _connectedEndpoints.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<String, ByteArray>> = _incomingBytes.asSharedFlow()

    private val _disconnectedEndpoint = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val disconnectedEndpoint: SharedFlow<String> = _disconnectedEndpoint.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var localServiceName: String = ""

    private val activeSockets = ConcurrentHashMap<String, Socket>()
    private val outputStreams = ConcurrentHashMap<String, DataOutputStream>()

    fun refreshLocalIps(): List<String> {
        val ips = getLocalIpAddresses()
        val port = serverSocket?.localPort ?: DEFAULT_PORT
        _status.update {
            it.copy(localEndpointInfo = "${ips.firstOrNull() ?: "No Wi-Fi/Hotspot IP"}:$port")
        }
        return ips
    }

    fun getLocalIpAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { addresses.add(it) }
                    }
                }
            }
        }
        return addresses
    }

    fun startHostServer(serviceDisplayName: String) {
        if (serverSocket != null && serverSocket?.isClosed == false) return
        scope.launch {
            try {
                val srv = runCatching { ServerSocket(DEFAULT_PORT) }.getOrElse { ServerSocket(0) }
                serverSocket = srv
                val actualPort = srv.localPort
                val ip = getLocalIpAddresses().firstOrNull() ?: "0.0.0.0"
                _status.update {
                    it.copy(
                        status = RadioStatus.ACTIVE_SERVER,
                        localEndpointInfo = "$ip:$actualPort",
                        errorMessage = null
                    )
                }
                registerNsdService(serviceDisplayName, actualPort)

                acceptJob = launch {
                    while (isActive && !srv.isClosed) {
                        val client = runCatching { srv.accept() }.getOrNull() ?: break
                        val epId = "wifi:${client.inetAddress.hostAddress}:${client.port}"
                        handleSocket(epId, client.inetAddress.hostAddress ?: "Wi-Fi Peer", client)
                    }
                }
            } catch (e: Exception) {
                _status.update {
                    it.copy(
                        status = RadioStatus.ERROR,
                        errorMessage = "Could not start Wi-Fi server: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun stopHostServer() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        unregisterNsd()
        _status.update { it.copy(status = RadioStatus.IDLE, errorMessage = null) }
    }

    fun startNsdDiscovery() {
        stopNsdDiscovery()
        _discoveredPeers.value = emptyList()
        val mgr = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _status.update { it.copy(status = RadioStatus.DISCOVERING, errorMessage = null) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != NSD_SERVICE_TYPE) return
                if (serviceInfo.serviceName == localServiceName) return
                mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        val epId = "wifi:$host:$port"
                        _discoveredPeers.update { list ->
                            list.filterNot { it.id == epId } + DiscoveredPeer(
                                id = epId,
                                name = resolved.serviceName,
                                transportKind = TransportKind.WIFI_LAN_HOTSPOT,
                                addressLabel = "$host:$port"
                            )
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _discoveredPeers.update { list -> list.filterNot { it.name == serviceInfo.serviceName } }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _status.update { it.copy(status = RadioStatus.IDLE) }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _status.update {
                    it.copy(status = RadioStatus.ERROR, errorMessage = "NSD discovery failed ($errorCode)")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        runCatching {
            mgr.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { e ->
            _status.update {
                it.copy(status = RadioStatus.ERROR, errorMessage = "NSD unavailable: ${e.localizedMessage}")
            }
        }
    }

    fun stopNsdDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
    }

    fun connectToHost(host: String, port: Int = DEFAULT_PORT, displayLabel: String? = null) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) {
            _status.update { it.copy(errorMessage = "Enter a valid host IP address") }
            return
        }
        scope.launch {
            _status.update { it.copy(status = RadioStatus.CONNECTING, errorMessage = null) }
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(cleanHost, port), 6000)
                val epId = "wifi:$cleanHost:$port"
                handleSocket(epId, displayLabel ?: cleanHost, socket)
                _status.update { it.copy(status = RadioStatus.IDLE, errorMessage = null) }
            } catch (e: Exception) {
                _status.update {
                    it.copy(
                        status = RadioStatus.ERROR,
                        errorMessage = "Failed to connect to $cleanHost:$port (${e.localizedMessage})"
                    )
                }
            }
        }
    }

    private fun handleSocket(endpointId: String, peerName: String, socket: Socket) {
        activeSockets[endpointId] = socket
        outputStreams[endpointId] = DataOutputStream(socket.getOutputStream())
        _connectedEndpoints.update { it + (endpointId to peerName) }

        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (isActive && !socket.isClosed) {
                    val length = input.readInt()
                    if (length <= 0 || length > PacketValidator.MAX_FRAME_BYTES) {
                        break
                    }
                    val buffer = ByteArray(length)
                    input.readFully(buffer)
                    _incomingBytes.tryEmit(endpointId to buffer)
                }
            } catch (_: Exception) {
                // Socket closed or peer disconnected
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
        if (existed) {
            _disconnectedEndpoint.tryEmit(endpointId)
        }
    }

    private fun registerNsdService(displayName: String, port: Int) {
        unregisterNsd()
        val mgr = nsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "OM-${displayName.take(18)}"
            serviceType = NSD_SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                localServiceName = serviceInfo.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun unregisterNsd() {
        registrationListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        registrationListener = null
    }
}
