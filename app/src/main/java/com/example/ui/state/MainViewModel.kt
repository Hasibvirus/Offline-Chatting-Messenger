package com.example.ui.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.security.CryptoEngine
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationType
import com.example.data.local.MemberEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PollEntity
import com.example.data.local.PollVoteEntity
import com.example.data.local.SosAlertEntity
import com.example.data.local.ThemeMode
import com.example.data.local.TransportKind
import com.example.data.local.UserPreferences
import com.example.network.audio.PttRadioState
import com.example.network.audio.VoiceRecorderState
import com.example.network.transport.ConnectedTransportPeer
import com.example.network.transport.DiscoveredPeer
import com.example.network.transport.FileTransferProgress
import com.example.network.transport.PendingConnectionRequest
import com.example.network.transport.TransportStatusSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val session = container.sessionManager

    val preferences: StateFlow<UserPreferences> = repo.preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val conversations: StateFlow<List<ConversationEntity>> = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedPeers: StateFlow<List<ConnectedTransportPeer>> = session.connectedPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyStatus: StateFlow<TransportStatusSnapshot> = session.nearbyTransport.status
    val nearbyDiscovered: StateFlow<List<DiscoveredPeer>> = session.nearbyTransport.discoveredPeers
    val nearbyPending: StateFlow<List<PendingConnectionRequest>> = session.nearbyTransport.pendingRequests
    val nearbyConnectedRaw: StateFlow<Map<String, String>> = session.nearbyTransport.connectedEndpoints

    val wifiStatus: StateFlow<TransportStatusSnapshot> = session.wifiTransport.status
    val wifiDiscovered: StateFlow<List<DiscoveredPeer>> = session.wifiTransport.discoveredPeers
    val wifiConnectedRaw: StateFlow<Map<String, String>> = session.wifiTransport.connectedEndpoints

    val btStatus: StateFlow<TransportStatusSnapshot> = session.bluetoothTransport.status
    val btDiscovered: StateFlow<List<DiscoveredPeer>> = session.bluetoothTransport.discoveredPeers
    val btPaired: StateFlow<List<DiscoveredPeer>> = session.bluetoothTransport.pairedPeers
    val btConnectedRaw: StateFlow<Map<String, String>> = session.bluetoothTransport.connectedEndpoints

    val fileTransfers: StateFlow<Map<String, FileTransferProgress>> = session.fileTransfers
    val fileMessages: StateFlow<List<MessageEntity>> = repo.observeFileMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val voiceMessages: StateFlow<List<MessageEntity>> = repo.observeVoiceMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPolls: StateFlow<List<PollEntity>> = repo.observeAllPolls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allVotes: StateFlow<List<PollVoteEntity>> = repo.observeAllVotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sosAlerts: StateFlow<List<SosAlertEntity>> = repo.observeSosAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pttState: StateFlow<PttRadioState> = session.walkieEngine.state
    val pttAudioLevel: StateFlow<Float> = session.walkieEngine.audioLevel
    val pttActiveSpeaker: StateFlow<String?> = session.walkieEngine.activeSpeaker

    val voiceState: StateFlow<VoiceRecorderState> = container.voiceRecorderPlayer.state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val historyMessages: StateFlow<List<MessageEntity>> = _searchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) repo.observeAllRecentMessages() else repo.searchMessages(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    val sosBannerEvents: SharedFlow<SosAlertEntity> = session.sosBannerEvents

    init {
        viewModelScope.launch {
            repo.preferencesStore.ensureDeviceId()
        }
        viewModelScope.launch {
            session.pairingFeedback.collect { msg ->
                _uiMessages.tryEmit(msg)
            }
        }
    }

    fun observeConversation(id: String): Flow<ConversationEntity?> = repo.observeConversation(id)
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> = repo.observeMessages(conversationId)
    fun observeMembers(conversationId: String): Flow<List<MemberEntity>> = repo.observeMembers(conversationId)
    fun observePolls(conversationId: String): Flow<List<PollEntity>> = repo.observePolls(conversationId)
    fun observeVotes(conversationId: String): Flow<List<PollVoteEntity>> = repo.observeVotes(conversationId)

    fun markOnboardingDone() {
        viewModelScope.launch { repo.preferencesStore.markOnboardingCompleted() }
    }

    fun saveProfile(name: String, bio: String, colorIdx: Int, lang: String) {
        viewModelScope.launch {
            repo.preferencesStore.saveProfile(name, bio, colorIdx, lang)
            _uiMessages.tryEmit("Profile saved")
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { repo.preferencesStore.setLanguage(lang) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.preferencesStore.setThemeMode(mode) }
    }

    fun setPreferredTransport(kind: TransportKind) {
        viewModelScope.launch { repo.preferencesStore.setPreferredTransport(kind) }
    }

    suspend fun createPrivateRoom(title: String): Pair<ConversationEntity, String> {
        val prefs = preferences.value
        val deviceId = repo.preferencesStore.ensureDeviceId()
        val pin = CryptoEngine.generatePairingPin()
        val conv = repo.createRoom(
            title = title.ifBlank { "Private Room" },
            description = "End-to-End Encrypted 1-to-1 Room",
            type = ConversationType.PRIVATE,
            hostDeviceId = deviceId,
            hostName = prefs.displayName.ifBlank { deviceId },
            transport = prefs.preferredTransport,
            isLocalHost = true,
            customPin = pin
        )
        session.registerHostRoomPin(conv.roomCode, pin)
        startHostingRadio(conv.preferredTransport, prefs.displayName.ifBlank { deviceId })
        return conv to pin
    }

    suspend fun createGroupRoom(
        title: String,
        description: String,
        transport: TransportKind
    ): Pair<ConversationEntity, String> {
        val prefs = preferences.value
        val deviceId = repo.preferencesStore.ensureDeviceId()
        val pin = CryptoEngine.generatePairingPin()
        val conv = repo.createRoom(
            title = title,
            description = description,
            type = ConversationType.GROUP,
            hostDeviceId = deviceId,
            hostName = prefs.displayName.ifBlank { deviceId },
            transport = transport,
            isLocalHost = true,
            customPin = pin
        )
        session.registerHostRoomPin(conv.roomCode, pin)
        startHostingRadio(transport, prefs.displayName.ifBlank { deviceId })
        return conv to pin
    }

    suspend fun joinWithCode(
        roomCode: String,
        pin: String,
        transport: TransportKind,
        manualHostIp: String,
        isGroup: Boolean
    ): ConversationEntity {
        val prefs = preferences.value
        val deviceId = repo.preferencesStore.ensureDeviceId()
        val name = prefs.displayName.ifBlank { deviceId }
        val normalized = roomCode.trim().uppercase()

        val conv = repo.createRoom(
            title = if (isGroup) "Group $normalized" else "Private $normalized",
            description = "Joined via Code",
            type = if (isGroup) ConversationType.GROUP else ConversationType.PRIVATE,
            hostDeviceId = deviceId,
            hostName = name,
            transport = transport,
            isLocalHost = false,
            customRoomCode = normalized,
            customPin = pin.trim()
        )

        if (transport == TransportKind.WIFI_LAN_HOTSPOT && manualHostIp.isNotBlank()) {
            val parts = manualHostIp.trim().split(":")
            val ip = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 48219
            session.wifiTransport.connectToHost(ip, port, "Host $ip")
        }

        // Pair across any already-connected endpoints on this transport
        val endpoints = when (transport) {
            TransportKind.NEARBY_CONNECTIONS -> nearbyConnectedRaw.value.keys
            TransportKind.WIFI_LAN_HOTSPOT -> wifiConnectedRaw.value.keys
            TransportKind.BLUETOOTH_CLASSIC -> btConnectedRaw.value.keys
        }
        endpoints.forEach { epId ->
            session.initiatePairing(
                transportKind = transport,
                endpointId = epId,
                roomCode = normalized,
                pairingPin = pin.trim(),
                conversationType = conv.type,
                title = conv.title
            )
        }
        return conv
    }

    fun pairConnectedEndpoint(
        transportKind: TransportKind,
        endpointId: String,
        roomCode: String,
        pin: String,
        type: ConversationType,
        title: String
    ) {
        viewModelScope.launch {
            session.initiatePairing(transportKind, endpointId, roomCode, pin, type, title)
        }
    }

    suspend fun getPairingPinForRoom(conversationId: String): String {
        return repo.getPairingPin(conversationId) ?: "------"
    }

    fun startHostingRadio(transport: TransportKind, localName: String) {
        when (transport) {
            TransportKind.NEARBY_CONNECTIONS -> session.nearbyTransport.startAdvertising(localName)
            TransportKind.WIFI_LAN_HOTSPOT -> session.wifiTransport.startHostServer(localName)
            TransportKind.BLUETOOTH_CLASSIC -> session.bluetoothTransport.startRfcommServer()
        }
    }

    // Nearby controls
    fun startNearbyAdvertise() =
        session.nearbyTransport.startAdvertising(preferences.value.displayName.ifBlank { "OfflinePeer" })

    fun startNearbyDiscover() = session.nearbyTransport.startDiscovery()
    fun stopNearby() = session.nearbyTransport.stopAllRadio()
    fun requestNearbyConnect(endpointId: String) = session.nearbyTransport.requestConnection(
        preferences.value.displayName.ifBlank { "OfflinePeer" },
        endpointId
    )
    fun acceptNearby(endpointId: String) = session.nearbyTransport.acceptConnection(endpointId)
    fun rejectNearby(endpointId: String) = session.nearbyTransport.rejectConnection(endpointId)

    // Wi-Fi controls
    fun refreshWifiIps() = session.wifiTransport.refreshLocalIps()
    fun startWifiServer() =
        session.wifiTransport.startHostServer(preferences.value.displayName.ifBlank { "OfflineHost" })
    fun stopWifiServer() = session.wifiTransport.stopHostServer()
    fun startWifiNsdScan() = session.wifiTransport.startNsdDiscovery()
    fun stopWifiNsdScan() = session.wifiTransport.stopNsdDiscovery()
    fun connectWifiHost(host: String, port: Int) = session.wifiTransport.connectToHost(host, port)

    // Bluetooth controls
    fun refreshBtPaired() = session.bluetoothTransport.refreshPairedDevices()
    fun startBtServer() = session.bluetoothTransport.startRfcommServer()
    fun stopBtServer() = session.bluetoothTransport.stopRfcommServer()
    fun startBtDiscovery() = session.bluetoothTransport.startDiscovery()
    fun stopBtDiscovery() = session.bluetoothTransport.stopDiscovery()
    fun connectBtDevice(mac: String) = session.bluetoothTransport.connectToDevice(mac)

    fun disconnectPeer(kind: TransportKind, endpointId: String) =
        session.disconnectEndpoint(kind, endpointId)

    // Messaging & Features
    fun clearUnread(conversationId: String) {
        viewModelScope.launch { repo.clearUnread(conversationId) }
    }

    fun sendMessage(conversationId: String, text: String) {
        viewModelScope.launch {
            runCatching { session.sendTextMessage(conversationId, text) }
                .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to send") }
        }
    }

    fun shareSafFile(conversationId: String, uri: Uri) {
        viewModelScope.launch {
            val copyResult = repo.copySafUriToInternalStorage(uri)
            copyResult.onSuccess { (file, mime, _) ->
                session.sendFileOrVoice(conversationId, file, mime, isVoiceNote = false)
                _uiMessages.tryEmit("Sending ${file.name}")
            }.onFailure { err ->
                _uiMessages.tryEmit(err.localizedMessage ?: "File validation failed")
            }
        }
    }

    fun startVoiceRecording() {
        val res = container.voiceRecorderPlayer.startRecording(repo.voiceNotesDir)
        res.onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Cannot record audio") }
    }

    fun stopAndSendVoiceRecording(conversationId: String?) {
        val result = container.voiceRecorderPlayer.stopRecording()
        if (result == null) {
            _uiMessages.tryEmit("Recording too short")
            return
        }
        val (file, durationMs) = result
        viewModelScope.launch {
            if (conversationId != null) {
                session.sendFileOrVoice(
                    conversationId = conversationId,
                    file = file,
                    mimeType = "audio/mp4",
                    isVoiceNote = true,
                    voiceDurationMs = durationMs
                )
            } else {
                val targetId = conversations.value.firstOrNull()?.id
                if (targetId != null) {
                    session.sendFileOrVoice(targetId, file, "audio/mp4", true, durationMs)
                } else {
                    _uiMessages.tryEmit("Saved locally (${durationMs / 1000}s). Create a chat to send.")
                }
            }
        }
    }

    fun cancelVoiceRecording() = container.voiceRecorderPlayer.cancelRecording()
    fun toggleVoicePlayback(path: String) = container.voiceRecorderPlayer.togglePlayPause(path)

    fun startWalkieTalkie(conversationId: String?) {
        val targetId = conversationId ?: conversations.value.firstOrNull()?.id
        if (targetId == null) {
            _uiMessages.tryEmit("Join or create a room first to transmit Push-to-Talk")
            return
        }
        val res = session.walkieEngine.startTransmitting { pcmBytes ->
            session.transmitPttAudioFrame(targetId, pcmBytes)
        }
        res.onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Microphone error") }
    }

    fun stopWalkieTalkie() = session.walkieEngine.stopTransmitting()

    fun createPoll(conversationId: String, question: String, options: List<String>, allowMultiple: Boolean) {
        viewModelScope.launch {
            if (question.isBlank() || options.count { it.isNotBlank() } < 2) {
                _uiMessages.tryEmit("Enter a question and at least 2 options")
                return@launch
            }
            session.sendPoll(conversationId, question, options, allowMultiple)
        }
    }

    fun votePoll(conversationId: String, pollId: String, optionIndex: Int) {
        viewModelScope.launch { session.sendVote(conversationId, pollId, optionIndex) }
    }

    fun closePoll(pollId: String) {
        viewModelScope.launch { repo.closePoll(pollId) }
    }

    fun triggerEmergencySos(conversationId: String?, note: String) {
        viewModelScope.launch {
            session.broadcastSosAlert(conversationId, note)
            _uiMessages.tryEmit("Emergency SOS broadcast to connected nearby devices")
        }
    }

    fun acknowledgeSos(id: String) {
        viewModelScope.launch { repo.acknowledgeSosAlert(id) }
    }

    fun setHistorySearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { repo.deleteMessage(messageId) }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch { repo.deleteConversation(conversationId) }
    }

    fun updateGroupDetails(conversationId: String, title: String, description: String) {
        viewModelScope.launch { repo.updateConversationDetails(conversationId, title, description) }
    }

    fun kickMember(conversationId: String, deviceId: String) {
        viewModelScope.launch { session.kickMember(conversationId, deviceId) }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            repo.clearAllLocalData()
            _uiMessages.tryEmit("Local history and cached files cleared")
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
