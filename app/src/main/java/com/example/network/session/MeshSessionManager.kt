package com.example.network.session

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.security.CryptoEngine
import com.example.core.security.PacketValidator
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationType
import com.example.data.local.DeliveryStatus
import com.example.data.local.MemberEntity
import com.example.data.local.MemberRole
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.OfflineMessengerRepository
import com.example.data.local.PeerConnectionStatus
import com.example.data.local.PollEntity
import com.example.data.local.SosAlertEntity
import com.example.data.local.TransportKind
import com.example.network.audio.WalkieTalkieEngine
import com.example.network.protocol.ChatPayload
import com.example.network.protocol.DeliveryAckPayload
import com.example.network.protocol.FileAckPayload
import com.example.network.protocol.FileChunkPayload
import com.example.network.protocol.FileMetaPayload
import com.example.network.protocol.HandshakeAckPayload
import com.example.network.protocol.HandshakeConfirmPayload
import com.example.network.protocol.HandshakeInitPayload
import com.example.network.protocol.KickPayload
import com.example.network.protocol.MemberSyncItem
import com.example.network.protocol.MemberSyncPayload
import com.example.network.protocol.PacketType
import com.example.network.protocol.PollCreatePayload
import com.example.network.protocol.PollVotePayload
import com.example.network.protocol.PttFramePayload
import com.example.network.protocol.SosPayload
import com.example.network.protocol.WirePacket
import com.example.network.transport.BluetoothClassicTransport
import com.example.network.transport.ConnectedTransportPeer
import com.example.network.transport.FileTransferProgress
import com.example.network.transport.NearbyConnectionsTransport
import com.example.network.transport.TransferStatus
import com.example.network.transport.WifiLanTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class MeshSessionManager(
    private val context: Context,
    private val repository: OfflineMessengerRepository,
    val nearbyTransport: NearbyConnectionsTransport,
    val wifiTransport: WifiLanTransport,
    val bluetoothTransport: BluetoothClassicTransport,
    val walkieEngine: WalkieTalkieEngine
) {
    companion object {
        private const val SOS_CHANNEL_ID = "offline_sos_alerts"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectedPeers = MutableStateFlow<List<ConnectedTransportPeer>>(emptyList())
    val connectedPeers: StateFlow<List<ConnectedTransportPeer>> = _connectedPeers.asStateFlow()

    private val _fileTransfers = MutableStateFlow<Map<String, FileTransferProgress>>(emptyMap())
    val fileTransfers: StateFlow<Map<String, FileTransferProgress>> = _fileTransfers.asStateFlow()

    private val _sosBannerEvents = MutableSharedFlow<SosAlertEntity>(extraBufferCapacity = 8)
    val sosBannerEvents: SharedFlow<SosAlertEntity> = _sosBannerEvents.asSharedFlow()

    private val _pairingFeedback = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val pairingFeedback: SharedFlow<String> = _pairingFeedback.asSharedFlow()

    // Per-endpoint authenticated room key & pending ECDH handshake state
    private data class PendingHandshake(
        val roomId: String,
        val roomCode: String,
        val pairingPin: String,
        val keyPair: KeyPair,
        val localNonceB64: String,
        var candidateAesKey: SecretKey? = null,
        var candidateMacKey: SecretKey? = null,
        var candidateSafetyCode: String = ""
    )

    private data class IncomingFileAssembly(
        val meta: FileMetaPayload,
        val conversationId: String,
        val senderId: String,
        val senderName: String,
        val chunks: MutableMap<Int, ByteArray> = ConcurrentHashMap(),
        var receivedBytes: Long = 0L
    )

    private val endpointSessionKeys = ConcurrentHashMap<String, SecretKey>()
    private val pendingHandshakes = ConcurrentHashMap<String, PendingHandshake>()
    private val incomingTransfers = ConcurrentHashMap<String, IncomingFileAssembly>()
    private var pttSequence = 0L

    init {
        ensureNotificationChannel()
        observeTransportStreams()
        scope.launch {
            while (true) {
                delay(400)
                walkieEngine.clearStaleReceivingState()
            }
        }
    }

    private fun observeTransportStreams() {
        scope.launch {
            nearbyTransport.incomingBytes.collect { (endpointId, bytes) ->
                handleRawPacket(TransportKind.NEARBY_CONNECTIONS, endpointId, bytes)
            }
        }
        scope.launch {
            wifiTransport.incomingBytes.collect { (endpointId, bytes) ->
                handleRawPacket(TransportKind.WIFI_LAN_HOTSPOT, endpointId, bytes)
            }
        }
        scope.launch {
            bluetoothTransport.incomingBytes.collect { (endpointId, bytes) ->
                handleRawPacket(TransportKind.BLUETOOTH_CLASSIC, endpointId, bytes)
            }
        }
        scope.launch {
            nearbyTransport.disconnectedEndpoint.collect { handleEndpointDisconnected(it) }
        }
        scope.launch {
            wifiTransport.disconnectedEndpoint.collect { handleEndpointDisconnected(it) }
        }
        scope.launch {
            bluetoothTransport.disconnectedEndpoint.collect { handleEndpointDisconnected(it) }
        }
    }

    private fun handleEndpointDisconnected(endpointId: String) {
        endpointSessionKeys.remove(endpointId)
        pendingHandshakes.remove(endpointId)
        val removed = _connectedPeers.value.firstOrNull { it.endpointId == endpointId }
        _connectedPeers.update { list -> list.filterNot { it.endpointId == endpointId } }
        if (removed != null) {
            scope.launch {
                repository.updatePeerStatusEverywhere(removed.deviceId, PeerConnectionStatus.DISCONNECTED)
            }
        }
        // Mark any in-progress incoming file transfers from disconnected peers as interrupted
        _fileTransfers.update { map ->
            map.mapValues { (_, transfer) ->
                if (transfer.status == TransferStatus.IN_PROGRESS && !transfer.isOutgoing) {
                    transfer.copy(
                        status = TransferStatus.INTERRUPTED,
                        errorMessage = "Connection interrupted before transfer completed"
                    )
                } else {
                    transfer
                }
            }
        }
    }

    suspend fun initiatePairing(
        transportKind: TransportKind,
        endpointId: String,
        roomCode: String,
        pairingPin: String,
        conversationType: ConversationType,
        title: String
    ) {
        val prefs = repository.preferencesStore.preferences.first()
        val deviceId = repository.preferencesStore.ensureDeviceId()
        val senderName = prefs.displayName.ifBlank { deviceId }
        val normalizedCode = roomCode.trim().uppercase()

        val conv = repository.createRoom(
            title = title.ifBlank { "Room $normalizedCode" },
            description = "",
            type = conversationType,
            hostDeviceId = deviceId,
            hostName = senderName,
            transport = transportKind,
            isLocalHost = false,
            customRoomCode = normalizedCode,
            customPin = pairingPin.trim()
        )

        val keyPair = CryptoEngine.generateEphemeralEcKeyPair()
        val nonceB64 = CryptoEngine.encodeB64(UUID.randomUUID().toString().toByteArray())
        pendingHandshakes[endpointId] = PendingHandshake(
            roomId = conv.id,
            roomCode = normalizedCode,
            pairingPin = pairingPin.trim(),
            keyPair = keyPair,
            localNonceB64 = nonceB64
        )

        val initPayload = HandshakeInitPayload(
            roomCode = normalizedCode,
            ecPubKeyB64 = CryptoEngine.encodeB64(keyPair.public.encoded),
            nonceB64 = nonceB64,
            conversationType = conversationType.name,
            title = conv.title
        )
        val packet = WirePacket(
            packetType = PacketType.HANDSHAKE_INIT,
            roomId = conv.id,
            packetId = UUID.randomUUID().toString(),
            senderId = deviceId,
            senderName = senderName,
            timestamp = System.currentTimeMillis(),
            payloadB64 = json.encodeToString(initPayload)
        )
        sendRawPacket(transportKind, endpointId, packet)
    }

    private suspend fun handleRawPacket(
        transportKind: TransportKind,
        endpointId: String,
        rawBytes: ByteArray
    ) {
        if (!PacketValidator.validateFrameSize(rawBytes)) return
        val packet = runCatching {
            json.decodeFromString<WirePacket>(rawBytes.decodeToString())
        }.getOrNull() ?: return

        if (packet.version != 1) return
        if (!PacketValidator.checkAndRecordPacket(packet.senderId, packet.packetId, packet.timestamp)) {
            return
        }

        when (packet.packetType) {
            PacketType.HANDSHAKE_INIT -> handleHandshakeInit(transportKind, endpointId, packet)
            PacketType.HANDSHAKE_ACK -> handleHandshakeAck(transportKind, endpointId, packet)
            PacketType.HANDSHAKE_CONFIRM -> handleHandshakeConfirm(transportKind, endpointId, packet)
            else -> handleEncryptedPacket(transportKind, endpointId, packet)
        }
    }

    private suspend fun handleHandshakeInit(
        transportKind: TransportKind,
        endpointId: String,
        packet: WirePacket
    ) {
        val init = runCatching {
            json.decodeFromString<HandshakeInitPayload>(packet.payloadB64)
        }.getOrNull() ?: return

        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }

        val conv = repository.getConversationByRoomCode(init.roomCode)
        if (conv == null) {
            val reject = HandshakeAckPayload(
                roomCode = init.roomCode,
                ecPubKeyB64 = "",
                nonceB64 = "",
                macB64 = "",
                accepted = false,
                reason = "Unknown room code on this host"
            )
            sendRawPacket(
                transportKind,
                endpointId,
                WirePacket(
                    packetType = PacketType.HANDSHAKE_ACK,
                    roomId = packet.roomId,
                    packetId = UUID.randomUUID().toString(),
                    senderId = localId,
                    senderName = localName,
                    timestamp = System.currentTimeMillis(),
                    payloadB64 = json.encodeToString(reject)
                )
            )
            return
        }

        val hostPin = activeRoomPins[conv.roomCode]
            ?: repository.getPairingPin(conv.id).orEmpty()
        val keyPair = CryptoEngine.generateEphemeralEcKeyPair()
        val localNonce = CryptoEngine.encodeB64(UUID.randomUUID().toString().toByteArray())
        val peerPubBytes = CryptoEngine.decodeB64(init.ecPubKeyB64)

        val derived = CryptoEngine.deriveSessionMaterial(
            localPrivateKey = keyPair.private,
            localPublicKeyBytes = keyPair.public.encoded,
            peerPublicKeyBytes = peerPubBytes,
            roomCode = conv.roomCode,
            pairingPin = hostPin
        )

        val hostMac = CryptoEngine.computeKeyConfirmationMac(
            macKey = derived.macKey,
            roleTag = "HOST",
            roomCode = conv.roomCode,
            nonceB64 = init.nonceB64
        )

        pendingHandshakes[endpointId] = PendingHandshake(
            roomId = conv.id,
            roomCode = conv.roomCode,
            pairingPin = hostPin,
            keyPair = keyPair,
            localNonceB64 = localNonce,
            candidateAesKey = derived.aesKey,
            candidateMacKey = derived.macKey,
            candidateSafetyCode = derived.safetyCode
        )

        val ack = HandshakeAckPayload(
            roomCode = conv.roomCode,
            ecPubKeyB64 = CryptoEngine.encodeB64(keyPair.public.encoded),
            nonceB64 = localNonce,
            macB64 = CryptoEngine.encodeB64(hostMac),
            accepted = true,
            conversationTitle = conv.title,
            conversationType = conv.type.name
        )
        sendRawPacket(
            transportKind,
            endpointId,
            WirePacket(
                packetType = PacketType.HANDSHAKE_ACK,
                roomId = conv.id,
                packetId = UUID.randomUUID().toString(),
                senderId = localId,
                senderName = localName,
                timestamp = System.currentTimeMillis(),
                payloadB64 = json.encodeToString(ack)
            )
        )
    }

    private suspend fun handleHandshakeAck(
        transportKind: TransportKind,
        endpointId: String,
        packet: WirePacket
    ) {
        val ack = runCatching {
            json.decodeFromString<HandshakeAckPayload>(packet.payloadB64)
        }.getOrNull() ?: return

        val pending = pendingHandshakes[endpointId] ?: return
        if (!ack.accepted) {
            pendingHandshakes.remove(endpointId)
            _pairingFeedback.tryEmit("Pairing rejected: ${ack.reason}")
            return
        }

        val peerPubBytes = CryptoEngine.decodeB64(ack.ecPubKeyB64)
        val derived = CryptoEngine.deriveSessionMaterial(
            localPrivateKey = pending.keyPair.private,
            localPublicKeyBytes = pending.keyPair.public.encoded,
            peerPublicKeyBytes = peerPubBytes,
            roomCode = pending.roomCode,
            pairingPin = pending.pairingPin
        )

        val expectedHostMac = CryptoEngine.computeKeyConfirmationMac(
            macKey = derived.macKey,
            roleTag = "HOST",
            roomCode = pending.roomCode,
            nonceB64 = pending.localNonceB64
        )
        val actualHostMac = CryptoEngine.decodeB64(ack.macB64)

        if (!CryptoEngine.verifyMacConstantTime(expectedHostMac, actualHostMac)) {
            pendingHandshakes.remove(endpointId)
            _pairingFeedback.tryEmit("Pairing failed: incorrect PIN or unauthenticated peer")
            return
        }

        val peerMac = CryptoEngine.computeKeyConfirmationMac(
            macKey = derived.macKey,
            roleTag = "PEER",
            roomCode = pending.roomCode,
            nonceB64 = ack.nonceB64
        )

        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }

        val confirm = HandshakeConfirmPayload(
            roomCode = pending.roomCode,
            macB64 = CryptoEngine.encodeB64(peerMac)
        )
        sendRawPacket(
            transportKind,
            endpointId,
            WirePacket(
                packetType = PacketType.HANDSHAKE_CONFIRM,
                roomId = pending.roomId,
                packetId = UUID.randomUUID().toString(),
                senderId = localId,
                senderName = localName,
                timestamp = System.currentTimeMillis(),
                payloadB64 = json.encodeToString(confirm)
            )
        )

        finalizeAuthenticatedPeer(
            transportKind = transportKind,
            endpointId = endpointId,
            roomId = pending.roomId,
            roomCode = pending.roomCode,
            peerDeviceId = packet.senderId,
            peerName = packet.senderName,
            aesKey = derived.aesKey,
            safetyCode = derived.safetyCode,
            remoteTitle = ack.conversationTitle
        )
        pendingHandshakes.remove(endpointId)
        _pairingFeedback.tryEmit("Encrypted tunnel verified • Safety Code ${derived.safetyCode}")
    }

    private suspend fun handleHandshakeConfirm(
        transportKind: TransportKind,
        endpointId: String,
        packet: WirePacket
    ) {
        val confirm = runCatching {
            json.decodeFromString<HandshakeConfirmPayload>(packet.payloadB64)
        }.getOrNull() ?: return

        val pending = pendingHandshakes[endpointId] ?: return
        val macKey = pending.candidateMacKey ?: return
        val aesKey = pending.candidateAesKey ?: return

        val expectedPeerMac = CryptoEngine.computeKeyConfirmationMac(
            macKey = macKey,
            roleTag = "PEER",
            roomCode = pending.roomCode,
            nonceB64 = pending.localNonceB64
        )
        val actualPeerMac = CryptoEngine.decodeB64(confirm.macB64)
        if (!CryptoEngine.verifyMacConstantTime(expectedPeerMac, actualPeerMac)) {
            pendingHandshakes.remove(endpointId)
            _pairingFeedback.tryEmit("Peer failed PIN authentication")
            return
        }

        finalizeAuthenticatedPeer(
            transportKind = transportKind,
            endpointId = endpointId,
            roomId = pending.roomId,
            roomCode = pending.roomCode,
            peerDeviceId = packet.senderId,
            peerName = packet.senderName,
            aesKey = aesKey,
            safetyCode = pending.candidateSafetyCode,
            remoteTitle = null
        )
        pendingHandshakes.remove(endpointId)
        broadcastMemberSync(pending.roomId)
        _pairingFeedback.tryEmit("${packet.senderName} verified • Safety Code ${pending.candidateSafetyCode}")
    }

    private suspend fun finalizeAuthenticatedPeer(
        transportKind: TransportKind,
        endpointId: String,
        roomId: String,
        roomCode: String,
        peerDeviceId: String,
        peerName: String,
        aesKey: SecretKey,
        safetyCode: String,
        remoteTitle: String?
    ) {
        endpointSessionKeys[endpointId] = aesKey
        repository.upgradeRoomSessionKey(roomId, aesKey, safetyCode)
        if (!remoteTitle.isNullOrBlank()) {
            repository.updateConversationDetails(roomId, remoteTitle, "")
        }
        repository.upsertMember(
            MemberEntity(
                conversationId = roomId,
                deviceId = peerDeviceId,
                displayName = peerName,
                role = MemberRole.MEMBER,
                connectionStatus = PeerConnectionStatus.CONNECTED,
                transportLabel = transportKind.name
            )
        )
        repository.insertSystemMessage(
            conversationId = roomId,
            text = "$peerName joined over ${transportKind.name} • ECDH Safety Code $safetyCode"
        )

        val peerKey = "$transportKind:$endpointId"
        _connectedPeers.update { list ->
            list.filterNot { it.endpointId == endpointId } + ConnectedTransportPeer(
                transportPeerKey = peerKey,
                endpointId = endpointId,
                deviceId = peerDeviceId,
                displayName = peerName,
                transportKind = transportKind,
                roomCode = roomCode,
                safetyCode = safetyCode,
                isAuthenticated = true
            )
        }
    }

    private suspend fun handleEncryptedPacket(
        transportKind: TransportKind,
        endpointId: String,
        packet: WirePacket
    ) {
        val conv = repository.getConversation(packet.roomId) ?: return
        val key = endpointSessionKeys[endpointId] ?: repository.getRoomSecretKey(conv.id) ?: return
        val iv = packet.ivB64 ?: return
        val aad = "${packet.roomId}|${packet.senderId}|${packet.packetId}|${packet.timestamp}"

        val plaintextBytes = runCatching {
            CryptoEngine.decrypt(key, iv, packet.payloadB64, aad)
        }.getOrNull() ?: return

        val plaintext = plaintextBytes.decodeToString()

        when (packet.packetType) {
            PacketType.CHAT_MESSAGE -> {
                val payload = runCatching { json.decodeFromString<ChatPayload>(plaintext) }.getOrNull() ?: return
                val validText = runCatching { PacketValidator.validateTextContent(payload.text) }
                    .getOrDefault(payload.text.take(PacketValidator.MAX_TEXT_CHARS))
                val msgType = runCatching { MessageType.valueOf(payload.messageType) }
                    .getOrDefault(MessageType.TEXT)

                repository.insertMessage(
                    MessageEntity(
                        id = payload.messageId,
                        conversationId = conv.id,
                        senderId = packet.senderId,
                        senderName = packet.senderName,
                        type = msgType,
                        content = validText,
                        attachmentName = payload.attachmentName,
                        attachmentMime = payload.attachmentMime,
                        attachmentSize = payload.attachmentSize,
                        voiceDurationMs = payload.voiceDurationMs,
                        deliveryStatus = DeliveryStatus.DELIVERED,
                        timestamp = packet.timestamp,
                        isIncoming = true
                    ),
                    unreadDelta = 1
                )
                sendDeliveryAck(transportKind, endpointId, conv.id, payload.messageId, key)
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
            }

            PacketType.DELIVERY_ACK -> {
                val ack = runCatching { json.decodeFromString<DeliveryAckPayload>(plaintext) }.getOrNull() ?: return
                repository.updateMessageStatus(ack.messageId, DeliveryStatus.DELIVERED)
            }

            PacketType.FILE_META -> {
                val meta = runCatching { json.decodeFromString<FileMetaPayload>(plaintext) }.getOrNull() ?: return
                val validated = PacketValidator.validateFileMeta(meta.fileName, meta.mimeType, meta.totalBytes)
                if (validated.isFailure) return

                incomingTransfers[meta.transferId] = IncomingFileAssembly(
                    meta = meta.copy(fileName = validated.getOrThrow()),
                    conversationId = conv.id,
                    senderId = packet.senderId,
                    senderName = packet.senderName
                )
                _fileTransfers.update {
                    it + (meta.transferId to FileTransferProgress(
                        transferId = meta.transferId,
                        conversationId = conv.id,
                        fileName = validated.getOrThrow(),
                        totalBytes = meta.totalBytes,
                        transferredBytes = 0L,
                        isOutgoing = false,
                        status = TransferStatus.IN_PROGRESS
                    ))
                }
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
            }

            PacketType.FILE_CHUNK -> {
                val chunk = runCatching { json.decodeFromString<FileChunkPayload>(plaintext) }.getOrNull() ?: return
                val assembly = incomingTransfers[chunk.transferId] ?: return
                val bytes = CryptoEngine.decodeB64(chunk.dataB64)
                if (assembly.chunks.putIfAbsent(chunk.chunkIndex, bytes) == null) {
                    assembly.receivedBytes += bytes.size
                }
                _fileTransfers.update { map ->
                    val existing = map[chunk.transferId] ?: return@update map
                    map + (chunk.transferId to existing.copy(transferredBytes = assembly.receivedBytes))
                }
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
                if (assembly.chunks.size == assembly.meta.totalChunks) {
                    finalizeIncomingFile(assembly, transportKind, endpointId, key)
                }
            }

            PacketType.POLL_CREATE -> {
                val payload = runCatching { json.decodeFromString<PollCreatePayload>(plaintext) }.getOrNull() ?: return
                val poll = PollEntity(
                    id = payload.pollId,
                    conversationId = conv.id,
                    question = payload.question,
                    optionsJson = json.encodeToString(payload.options),
                    createdById = packet.senderId,
                    createdByName = packet.senderName,
                    allowMultiple = payload.allowMultiple,
                    createdAt = packet.timestamp
                )
                repository.saveRemotePoll(poll)
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
            }

            PacketType.POLL_VOTE -> {
                val payload = runCatching { json.decodeFromString<PollVotePayload>(plaintext) }.getOrNull() ?: return
                repository.castVote(payload.pollId, packet.senderId, packet.senderName, payload.optionIndex)
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
            }

            PacketType.SOS_ALERT -> {
                val payload = runCatching { json.decodeFromString<SosPayload>(plaintext) }.getOrNull() ?: return
                val alert = SosAlertEntity(
                    id = payload.alertId,
                    conversationId = conv.id,
                    senderId = packet.senderId,
                    senderName = packet.senderName,
                    message = payload.message,
                    transportUsed = payload.transportLabel,
                    timestamp = packet.timestamp
                )
                repository.recordSosAlert(alert)
                repository.insertMessage(
                    MessageEntity(
                        id = "sos_${payload.alertId}",
                        conversationId = conv.id,
                        senderId = packet.senderId,
                        senderName = packet.senderName,
                        type = MessageType.SOS,
                        content = payload.message,
                        deliveryStatus = DeliveryStatus.DELIVERED,
                        timestamp = packet.timestamp,
                        isIncoming = true,
                        isEmergency = true
                    ),
                    unreadDelta = 1
                )
                _sosBannerEvents.tryEmit(alert)
                triggerSosFeedback(alert)
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
            }

            PacketType.PTT_AUDIO_FRAME -> {
                val frame = runCatching { json.decodeFromString<PttFramePayload>(plaintext) }.getOrNull() ?: return
                val pcm = CryptoEngine.decodeB64(frame.pcmB64)
                walkieEngine.playIncomingFrame(packet.senderName, pcm)
                if (conv.isLocalHost && conv.type == ConversationType.GROUP) {
                    relayToOtherPeers(conv, endpointId, packet, plaintextBytes)
                }
            }

            PacketType.MEMBER_LIST_SYNC -> {
                val sync = runCatching { json.decodeFromString<MemberSyncPayload>(plaintext) }.getOrNull() ?: return
                sync.members.forEach { item ->
                    repository.upsertMember(
                        MemberEntity(
                            conversationId = conv.id,
                            deviceId = item.deviceId,
                            displayName = item.displayName,
                            role = runCatching { MemberRole.valueOf(item.role) }.getOrDefault(MemberRole.MEMBER),
                            connectionStatus = runCatching {
                                PeerConnectionStatus.valueOf(item.status)
                            }.getOrDefault(PeerConnectionStatus.CONNECTED),
                            transportLabel = transportKind.name
                        )
                    )
                }
            }

            PacketType.MEMBER_KICK -> {
                val kick = runCatching { json.decodeFromString<KickPayload>(plaintext) }.getOrNull() ?: return
                val localId = repository.preferencesStore.ensureDeviceId()
                if (kick.targetDeviceId == localId) {
                    repository.insertSystemMessage(conv.id, "Removed from group: ${kick.reason}")
                    disconnectEndpoint(transportKind, endpointId)
                } else {
                    repository.removeMember(conv.id, kick.targetDeviceId)
                }
            }

            else -> Unit
        }
    }

    private suspend fun finalizeIncomingFile(
        assembly: IncomingFileAssembly,
        transportKind: TransportKind,
        endpointId: String,
        key: SecretKey
    ) {
        incomingTransfers.remove(assembly.meta.transferId)
        val combined = ByteArray(assembly.meta.totalBytes.toInt())
        var offset = 0
        for (i in 0 until assembly.meta.totalChunks) {
            val part = assembly.chunks[i] ?: run {
                _fileTransfers.update { map ->
                    map + (assembly.meta.transferId to FileTransferProgress(
                        transferId = assembly.meta.transferId,
                        conversationId = assembly.conversationId,
                        fileName = assembly.meta.fileName,
                        totalBytes = assembly.meta.totalBytes,
                        transferredBytes = assembly.receivedBytes,
                        isOutgoing = false,
                        status = TransferStatus.INTERRUPTED,
                        errorMessage = "Missing chunk #$i"
                    ))
                }
                return
            }
            System.arraycopy(part, 0, combined, offset, part.size)
            offset += part.size
        }

        val actualHash = CryptoEngine.sha256Hex(combined)
        if (!actualHash.equals(assembly.meta.sha256Hex, ignoreCase = true)) {
            _fileTransfers.update { map ->
                map + (assembly.meta.transferId to FileTransferProgress(
                    transferId = assembly.meta.transferId,
                    conversationId = assembly.conversationId,
                    fileName = assembly.meta.fileName,
                    totalBytes = assembly.meta.totalBytes,
                    transferredBytes = assembly.receivedBytes,
                    isOutgoing = false,
                    status = TransferStatus.FAILED,
                    errorMessage = "Integrity checksum mismatch"
                ))
            }
            return
        }

        val targetDir = if (assembly.meta.isVoiceNote) repository.voiceNotesDir else repository.sharedFilesDir
        val outFile = PacketValidator.resolveSafeChildFile(targetDir, assembly.meta.fileName)
        outFile.writeBytes(combined)

        val msgType = when {
            assembly.meta.isVoiceNote -> MessageType.VOICE
            assembly.meta.mimeType.startsWith("image/") -> MessageType.IMAGE
            else -> MessageType.FILE
        }

        repository.insertMessage(
            MessageEntity(
                id = assembly.meta.messageId,
                conversationId = assembly.conversationId,
                senderId = assembly.senderId,
                senderName = assembly.senderName,
                type = msgType,
                content = if (assembly.meta.isVoiceNote) "Voice message" else assembly.meta.fileName,
                attachmentPath = outFile.absolutePath,
                attachmentName = assembly.meta.fileName,
                attachmentMime = assembly.meta.mimeType,
                attachmentSize = assembly.meta.totalBytes,
                voiceDurationMs = assembly.meta.voiceDurationMs,
                deliveryStatus = DeliveryStatus.DELIVERED,
                timestamp = System.currentTimeMillis(),
                isIncoming = true
            ),
            unreadDelta = 1
        )

        _fileTransfers.update { map ->
            map + (assembly.meta.transferId to FileTransferProgress(
                transferId = assembly.meta.transferId,
                conversationId = assembly.conversationId,
                fileName = assembly.meta.fileName,
                totalBytes = assembly.meta.totalBytes,
                transferredBytes = assembly.meta.totalBytes,
                isOutgoing = false,
                status = TransferStatus.COMPLETED
            ))
        }
        sendDeliveryAck(transportKind, endpointId, assembly.conversationId, assembly.meta.messageId, key)
    }

    // Active host pins kept strictly in memory during session
    private val activeRoomPins = ConcurrentHashMap<String, String>()

    fun registerHostRoomPin(roomCode: String, pin: String) {
        activeRoomPins[roomCode.trim().uppercase()] = pin.trim()
    }

    suspend fun sendTextMessage(conversationId: String, text: String) {
        val cleanText = PacketValidator.validateTextContent(text)
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        repository.insertMessage(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = localId,
                senderName = localName,
                type = MessageType.TEXT,
                content = cleanText,
                deliveryStatus = DeliveryStatus.SENDING,
                timestamp = now,
                isIncoming = false
            )
        )

        val payload = ChatPayload(
            messageId = messageId,
            messageType = MessageType.TEXT.name,
            text = cleanText
        )
        val sentCount = broadcastEncrypted(
            conversationId = conversationId,
            packetType = PacketType.CHAT_MESSAGE,
            plaintextBytes = json.encodeToString(payload).toByteArray()
        )
        repository.updateMessageStatus(
            messageId,
            if (sentCount > 0) DeliveryStatus.SENT else DeliveryStatus.SENT
        )
    }

    suspend fun sendFileOrVoice(
        conversationId: String,
        file: File,
        mimeType: String,
        isVoiceNote: Boolean = false,
        voiceDurationMs: Long = 0L
    ) {
        val bytes = file.readBytes()
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }
        val messageId = UUID.randomUUID().toString()
        val transferId = UUID.randomUUID().toString()
        val totalChunks = ((bytes.size + PacketValidator.FILE_CHUNK_SIZE - 1) / PacketValidator.FILE_CHUNK_SIZE)
            .coerceAtLeast(1)

        val msgType = when {
            isVoiceNote -> MessageType.VOICE
            mimeType.startsWith("image/") -> MessageType.IMAGE
            else -> MessageType.FILE
        }

        repository.insertMessage(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = localId,
                senderName = localName,
                type = msgType,
                content = if (isVoiceNote) "Voice message" else file.name,
                attachmentPath = file.absolutePath,
                attachmentName = file.name,
                attachmentMime = mimeType,
                attachmentSize = bytes.size.toLong(),
                voiceDurationMs = voiceDurationMs,
                deliveryStatus = DeliveryStatus.SENDING,
                timestamp = System.currentTimeMillis(),
                isIncoming = false
            )
        )

        _fileTransfers.update {
            it + (transferId to FileTransferProgress(
                transferId = transferId,
                conversationId = conversationId,
                fileName = file.name,
                totalBytes = bytes.size.toLong(),
                transferredBytes = 0L,
                isOutgoing = true,
                status = TransferStatus.IN_PROGRESS
            ))
        }

        val meta = FileMetaPayload(
            transferId = transferId,
            messageId = messageId,
            fileName = file.name,
            mimeType = mimeType,
            totalBytes = bytes.size.toLong(),
            totalChunks = totalChunks,
            sha256Hex = CryptoEngine.sha256Hex(bytes),
            isVoiceNote = isVoiceNote,
            voiceDurationMs = voiceDurationMs
        )
        broadcastEncrypted(
            conversationId,
            PacketType.FILE_META,
            json.encodeToString(meta).toByteArray()
        )

        var sentBytes = 0L
        for (i in 0 until totalChunks) {
            val start = i * PacketValidator.FILE_CHUNK_SIZE
            val end = minOf(bytes.size, start + PacketValidator.FILE_CHUNK_SIZE)
            val slice = bytes.copyOfRange(start, end)
            val chunk = FileChunkPayload(
                transferId = transferId,
                chunkIndex = i,
                dataB64 = CryptoEngine.encodeB64(slice)
            )
            broadcastEncrypted(
                conversationId,
                PacketType.FILE_CHUNK,
                json.encodeToString(chunk).toByteArray()
            )
            sentBytes += slice.size
            _fileTransfers.update { map ->
                val cur = map[transferId] ?: return@update map
                map + (transferId to cur.copy(transferredBytes = sentBytes))
            }
        }

        _fileTransfers.update { map ->
            val cur = map[transferId] ?: return@update map
            map + (transferId to cur.copy(status = TransferStatus.COMPLETED))
        }
        repository.updateMessageStatus(messageId, DeliveryStatus.SENT)
    }

    suspend fun sendPoll(
        conversationId: String,
        question: String,
        options: List<String>,
        allowMultiple: Boolean
    ) {
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }
        val poll = repository.createPoll(conversationId, question, options, localId, localName, allowMultiple)
        val payload = PollCreatePayload(
            pollId = poll.id,
            question = poll.question,
            options = options.map { it.trim() }.filter { it.isNotEmpty() },
            allowMultiple = allowMultiple
        )
        broadcastEncrypted(
            conversationId,
            PacketType.POLL_CREATE,
            json.encodeToString(payload).toByteArray()
        )
    }

    suspend fun sendVote(conversationId: String, pollId: String, optionIndex: Int) {
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }
        repository.castVote(pollId, localId, localName, optionIndex)
        val payload = PollVotePayload(pollId = pollId, optionIndex = optionIndex)
        broadcastEncrypted(
            conversationId,
            PacketType.POLL_VOTE,
            json.encodeToString(payload).toByteArray()
        )
    }

    suspend fun broadcastSosAlert(conversationId: String?, customNote: String) {
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }
        val text = customNote.trim().ifBlank { "EMERGENCY SOS — Immediate local assistance requested!" }
        val alertId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val targetRooms = if (conversationId != null) {
            listOfNotNull(repository.getConversation(conversationId))
        } else {
            repository.observeConversations().first()
        }

        val activeTransportNames = _connectedPeers.value.map { it.transportKind.name }.distinct()
            .joinToString(", ").ifBlank { "Local Radio Standby" }

        val alert = SosAlertEntity(
            id = alertId,
            conversationId = targetRooms.firstOrNull()?.id ?: "LOCAL_BROADCAST",
            senderId = localId,
            senderName = localName,
            message = text,
            transportUsed = activeTransportNames,
            timestamp = now
        )
        repository.recordSosAlert(alert)
        triggerSosFeedback(alert)

        targetRooms.forEach { room ->
            repository.insertMessage(
                MessageEntity(
                    id = "sos_${alertId}_${room.id}",
                    conversationId = room.id,
                    senderId = localId,
                    senderName = localName,
                    type = MessageType.SOS,
                    content = text,
                    deliveryStatus = DeliveryStatus.SENT,
                    timestamp = now,
                    isIncoming = false,
                    isEmergency = true
                )
            )
            val payload = SosPayload(alertId, text, activeTransportNames)
            broadcastEncrypted(room.id, PacketType.SOS_ALERT, json.encodeToString(payload).toByteArray())
        }
    }

    fun transmitPttAudioFrame(conversationId: String, pcmBytes: ByteArray) {
        scope.launch {
            val payload = PttFramePayload(
                sequence = ++pttSequence,
                pcmB64 = CryptoEngine.encodeB64(pcmBytes)
            )
            broadcastEncrypted(
                conversationId,
                PacketType.PTT_AUDIO_FRAME,
                json.encodeToString(payload).toByteArray()
            )
        }
    }

    suspend fun kickMember(conversationId: String, targetDeviceId: String) {
        val payload = KickPayload(targetDeviceId = targetDeviceId)
        broadcastEncrypted(
            conversationId,
            PacketType.MEMBER_KICK,
            json.encodeToString(payload).toByteArray()
        )
        repository.removeMember(conversationId, targetDeviceId)
        _connectedPeers.value.filter { it.deviceId == targetDeviceId }.forEach { peer ->
            disconnectEndpoint(peer.transportKind, peer.endpointId)
        }
    }

    private suspend fun broadcastMemberSync(conversationId: String) {
        val members = repository.getMembers(conversationId).map {
            MemberSyncItem(it.deviceId, it.displayName, it.role.name, it.connectionStatus.name)
        }
        broadcastEncrypted(
            conversationId,
            PacketType.MEMBER_LIST_SYNC,
            json.encodeToString(MemberSyncPayload(members)).toByteArray()
        )
    }

    private suspend fun broadcastEncrypted(
        conversationId: String,
        packetType: PacketType,
        plaintextBytes: ByteArray
    ): Int {
        val conv = repository.getConversation(conversationId) ?: return 0
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }

        val targetPeers = _connectedPeers.value.filter {
            it.isAuthenticated && (it.roomCode == conv.roomCode || it.roomCode.isBlank())
        }
        var sentCount = 0
        val packetId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val aad = "$conversationId|$localId|$packetId|$now"

        targetPeers.forEach { peer ->
            val key = endpointSessionKeys[peer.endpointId] ?: repository.getRoomSecretKey(conversationId)
            if (key != null) {
                val encrypted = CryptoEngine.encrypt(key, plaintextBytes, aad)
                val packet = WirePacket(
                    packetType = packetType,
                    roomId = conversationId,
                    packetId = packetId,
                    senderId = localId,
                    senderName = localName,
                    timestamp = now,
                    ivB64 = encrypted.ivB64,
                    payloadB64 = encrypted.ciphertextB64
                )
                if (sendRawPacket(peer.transportKind, peer.endpointId, packet)) {
                    sentCount++
                }
            }
        }
        return sentCount
    }

    private suspend fun relayToOtherPeers(
        conv: ConversationEntity,
        sourceEndpointId: String,
        originalPacket: WirePacket,
        plaintextBytes: ByteArray
    ) {
        val peers = _connectedPeers.value.filter {
            it.isAuthenticated && it.roomCode == conv.roomCode && it.endpointId != sourceEndpointId
        }
        val aad = "${conv.id}|${originalPacket.senderId}|${originalPacket.packetId}|${originalPacket.timestamp}"
        peers.forEach { peer ->
            val key = endpointSessionKeys[peer.endpointId] ?: return@forEach
            val encrypted = CryptoEngine.encrypt(key, plaintextBytes, aad)
            val relayed = originalPacket.copy(
                ivB64 = encrypted.ivB64,
                payloadB64 = encrypted.ciphertextB64
            )
            sendRawPacket(peer.transportKind, peer.endpointId, relayed)
        }
    }

    private suspend fun sendDeliveryAck(
        transportKind: TransportKind,
        endpointId: String,
        conversationId: String,
        messageId: String,
        key: SecretKey
    ) {
        val prefs = repository.preferencesStore.preferences.first()
        val localId = repository.preferencesStore.ensureDeviceId()
        val localName = prefs.displayName.ifBlank { localId }
        val packetId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val aad = "$conversationId|$localId|$packetId|$now"
        val encrypted = CryptoEngine.encrypt(
            key,
            json.encodeToString(DeliveryAckPayload(messageId)).toByteArray(),
            aad
        )
        sendRawPacket(
            transportKind,
            endpointId,
            WirePacket(
                packetType = PacketType.DELIVERY_ACK,
                roomId = conversationId,
                packetId = packetId,
                senderId = localId,
                senderName = localName,
                timestamp = now,
                ivB64 = encrypted.ivB64,
                payloadB64 = encrypted.ciphertextB64
            )
        )
    }

    private fun sendRawPacket(
        transportKind: TransportKind,
        endpointId: String,
        packet: WirePacket
    ): Boolean {
        val bytes = json.encodeToString(packet).toByteArray()
        return when (transportKind) {
            TransportKind.NEARBY_CONNECTIONS -> nearbyTransport.sendBytes(endpointId, bytes)
            TransportKind.WIFI_LAN_HOTSPOT -> wifiTransport.sendBytes(endpointId, bytes)
            TransportKind.BLUETOOTH_CLASSIC -> bluetoothTransport.sendBytes(endpointId, bytes)
        }
    }

    fun disconnectEndpoint(transportKind: TransportKind, endpointId: String) {
        when (transportKind) {
            TransportKind.NEARBY_CONNECTIONS -> nearbyTransport.disconnect(endpointId)
            TransportKind.WIFI_LAN_HOTSPOT -> wifiTransport.disconnect(endpointId)
            TransportKind.BLUETOOTH_CLASSIC -> bluetoothTransport.disconnect(endpointId)
        }
    }

    private fun triggerSosFeedback(alert: SosAlertEntity) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 280, 140, 280), -1))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SOS Alert from ${alert.senderName}")
                .setContentText(alert.message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            mgr.notify(alert.id.hashCode(), notif)
        }
    }

    private fun ensureNotificationChannel() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            SOS_CHANNEL_ID,
            "Offline Emergency SOS",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts received from nearby connected peers during emergencies"
        }
        mgr.createNotificationChannel(channel)
    }
}
