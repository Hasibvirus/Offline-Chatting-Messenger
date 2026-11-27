package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.security.CryptoEngine
import com.example.core.security.PacketValidator
import com.example.core.security.RoomKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

class OfflineMessengerRepository(
    private val context: Context,
    private val db: OfflineMessengerDatabase,
    val preferencesStore: UserPreferencesDataStore
) {
    private val conversationDao = db.conversationDao()
    private val memberDao = db.memberDao()
    private val messageDao = db.messageDao()
    private val pollDao = db.pollDao()
    private val sosDao = db.sosDao()

    val sharedFilesDir: File
        get() = File(context.filesDir, "shared_files").apply { if (!exists()) mkdirs() }

    val voiceNotesDir: File
        get() = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()
    fun observeConversation(id: String): Flow<ConversationEntity?> = conversationDao.observeById(id)
    fun observeMembers(conversationId: String): Flow<List<MemberEntity>> =
        memberDao.observeForConversation(conversationId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    fun observeAllRecentMessages(): Flow<List<MessageEntity>> = messageDao.observeAllRecent()
    fun searchMessages(query: String): Flow<List<MessageEntity>> = messageDao.searchMessages(query)
    fun observeVoiceMessages(): Flow<List<MessageEntity>> = messageDao.observeVoiceMessages()
    fun observeFileMessages(): Flow<List<MessageEntity>> = messageDao.observeFileMessages()
    fun observePolls(conversationId: String): Flow<List<PollEntity>> =
        pollDao.observePolls(conversationId)

    fun observeAllPolls(): Flow<List<PollEntity>> = pollDao.observeAllPolls()
    fun observeVotes(conversationId: String): Flow<List<PollVoteEntity>> =
        pollDao.observeVotesForConversation(conversationId)

    fun observeAllVotes(): Flow<List<PollVoteEntity>> = pollDao.observeAllVotes()
    fun observeSosAlerts(): Flow<List<SosAlertEntity>> = sosDao.observeAll()

    suspend fun getConversation(id: String): ConversationEntity? = conversationDao.getById(id)
    suspend fun getConversationByRoomCode(code: String): ConversationEntity? =
        conversationDao.getByRoomCode(code.trim().uppercase())

    suspend fun getMembers(conversationId: String): List<MemberEntity> =
        memberDao.getForConversation(conversationId)

    suspend fun createRoom(
        title: String,
        description: String,
        type: ConversationType,
        hostDeviceId: String,
        hostName: String,
        transport: TransportKind,
        isLocalHost: Boolean,
        customRoomCode: String? = null,
        customPin: String? = null
    ): ConversationEntity {
        val roomCode = (customRoomCode?.trim()?.uppercase()).takeUnless { it.isNullOrBlank() }
            ?: CryptoEngine.generateRoomCode()
        val pin = (customPin?.trim()).takeUnless { it.isNullOrBlank() }
            ?: CryptoEngine.generatePairingPin()

        val existing = conversationDao.getByRoomCode(roomCode)
        if (existing != null) return existing

        val derivedKey = CryptoEngine.deriveRoomKeyFromSecret(roomCode, pin)
        val wrappedBlob = RoomKeyStore.wrapKey(derivedKey)
        val wrappedPin = RoomKeyStore.encryptSecretString(pin)
        val safetyCode = CryptoEngine.computeSafetyFingerprint(roomCode, pin)
        val pinHash = CryptoEngine.sha256Hex("$roomCode:$pin".toByteArray())
        val now = System.currentTimeMillis()
        val convId = UUID.randomUUID().toString()

        val conversation = ConversationEntity(
            id = convId,
            title = title.ifBlank { if (type == ConversationType.PRIVATE) "Private $roomCode" else "Group $roomCode" },
            description = description,
            type = type,
            roomCode = roomCode,
            pairingPinHash = pinHash,
            encryptedPinBlob = wrappedPin,
            encryptedKeyBlob = wrappedBlob,
            safetyCode = safetyCode,
            hostDeviceId = hostDeviceId,
            isLocalHost = isLocalHost,
            preferredTransport = transport,
            createdAt = now,
            lastMessagePreview = if (isLocalHost) "Room created • Code $roomCode" else "Joined room $roomCode",
            lastMessageAt = now
        )
        conversationDao.upsert(conversation)

        memberDao.upsert(
            MemberEntity(
                conversationId = convId,
                deviceId = hostDeviceId,
                displayName = hostName.ifBlank { hostDeviceId },
                role = if (isLocalHost) MemberRole.OWNER else MemberRole.MEMBER,
                connectionStatus = PeerConnectionStatus.CONNECTED,
                transportLabel = transport.name,
                joinedAt = now,
                lastSeenAt = now
            )
        )

        insertSystemMessage(
            conversationId = convId,
            text = "Encrypted room initialized (AES-256-GCM • Safety Code $safetyCode)"
        )
        return conversation
    }

    suspend fun getPairingPin(conversationId: String): String? {
        val conv = conversationDao.getById(conversationId) ?: return null
        return RoomKeyStore.decryptSecretString(conv.encryptedPinBlob)
    }

    suspend fun getRoomSecretKey(conversationId: String): SecretKey? {
        val conv = conversationDao.getById(conversationId) ?: return null
        return RoomKeyStore.unwrapKey(conv.encryptedKeyBlob)
    }

    suspend fun upgradeRoomSessionKey(
        conversationId: String,
        newAesKey: SecretKey,
        newSafetyCode: String
    ) {
        val wrapped = RoomKeyStore.wrapKey(newAesKey)
        conversationDao.updateSessionSecurity(conversationId, wrapped, newSafetyCode)
    }

    suspend fun upsertMember(member: MemberEntity) = memberDao.upsert(member)

    suspend fun updateMemberStatus(
        conversationId: String,
        deviceId: String,
        status: PeerConnectionStatus,
        transportLabel: String
    ) = memberDao.updateStatus(
        conversationId = conversationId,
        deviceId = deviceId,
        status = status,
        lastSeenAt = System.currentTimeMillis(),
        transportLabel = transportLabel
    )

    suspend fun updatePeerStatusEverywhere(deviceId: String, status: PeerConnectionStatus) =
        memberDao.updateStatusAcrossConversations(deviceId, status)

    suspend fun removeMember(conversationId: String, deviceId: String) =
        memberDao.removeMember(conversationId, deviceId)

    suspend fun updateConversationDetails(conversationId: String, title: String, description: String) =
        conversationDao.updateDetails(conversationId, title.trim(), description.trim())

    suspend fun deleteConversation(conversationId: String) =
        conversationDao.deleteById(conversationId)

    suspend fun clearUnread(conversationId: String) =
        conversationDao.clearUnread(conversationId)

    suspend fun insertMessage(message: MessageEntity, unreadDelta: Int = 0) {
        messageDao.upsert(message)
        val preview = when (message.type) {
            MessageType.IMAGE -> "Photo: ${message.attachmentName ?: "image"}"
            MessageType.FILE -> "File: ${message.attachmentName ?: "document"}"
            MessageType.VOICE -> "Voice message (${message.voiceDurationMs / 1000}s)"
            MessageType.POLL -> "Poll: ${message.content}"
            MessageType.SOS -> "SOS ALERT: ${message.content}"
            else -> message.content
        }
        conversationDao.updateLastMessage(
            id = message.conversationId,
            preview = preview.take(100),
            timestamp = message.timestamp,
            unreadDelta = unreadDelta
        )
    }

    suspend fun insertSystemMessage(conversationId: String, text: String) {
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = "SYSTEM",
            senderName = "System",
            type = MessageType.SYSTEM,
            content = text,
            deliveryStatus = DeliveryStatus.DELIVERED,
            timestamp = System.currentTimeMillis(),
            isIncoming = false
        )
        messageDao.upsert(msg)
    }

    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) =
        messageDao.updateDeliveryStatus(messageId, status)

    suspend fun deleteMessage(messageId: String) = messageDao.deleteById(messageId)

    suspend fun createPoll(
        conversationId: String,
        question: String,
        options: List<String>,
        creatorId: String,
        creatorName: String,
        allowMultiple: Boolean,
        pollId: String = UUID.randomUUID().toString()
    ): PollEntity {
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val poll = PollEntity(
            id = pollId,
            conversationId = conversationId,
            question = question.trim(),
            optionsJson = Json.encodeToString(cleanOptions),
            createdById = creatorId,
            createdByName = creatorName,
            allowMultiple = allowMultiple
        )
        pollDao.upsertPoll(poll)
        insertMessage(
            MessageEntity(
                id = "poll_msg_$pollId",
                conversationId = conversationId,
                senderId = creatorId,
                senderName = creatorName,
                type = MessageType.POLL,
                content = poll.question,
                pollId = poll.id,
                deliveryStatus = DeliveryStatus.DELIVERED,
                timestamp = poll.createdAt,
                isIncoming = false
            )
        )
        return poll
    }

    suspend fun saveRemotePoll(poll: PollEntity) {
        pollDao.upsertPoll(poll)
        insertMessage(
            MessageEntity(
                id = "poll_msg_${poll.id}",
                conversationId = poll.conversationId,
                senderId = poll.createdById,
                senderName = poll.createdByName,
                type = MessageType.POLL,
                content = poll.question,
                pollId = poll.id,
                deliveryStatus = DeliveryStatus.DELIVERED,
                timestamp = poll.createdAt,
                isIncoming = true
            ),
            unreadDelta = 1
        )
    }

    suspend fun castVote(pollId: String, voterId: String, voterName: String, optionIndex: Int) {
        val poll = pollDao.getPollById(pollId) ?: return
        pollDao.castVote(
            poll = poll,
            vote = PollVoteEntity(
                pollId = pollId,
                voterId = voterId,
                voterName = voterName,
                optionIndex = optionIndex
            )
        )
    }

    suspend fun closePoll(pollId: String) = pollDao.closePoll(pollId)

    suspend fun recordSosAlert(alert: SosAlertEntity) = sosDao.insert(alert)
    suspend fun acknowledgeSosAlert(id: String) = sosDao.acknowledge(id)

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        conversationDao.deleteAll()
        sosDao.clearAll()
        sharedFilesDir.listFiles()?.forEach { it.delete() }
        voiceNotesDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun copySafUriToInternalStorage(uri: Uri): Result<Triple<File, String, Long>> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                var displayName = "attachment_${System.currentTimeMillis()}"
                var sizeBytes = -1L

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }

                val targetFile = PacketValidator.resolveSafeChildFile(
                    sharedFilesDir,
                    "${UUID.randomUUID().toString().take(6)}_$displayName"
                )
                resolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > PacketValidator.MAX_FILE_BYTES) {
                                output.close()
                                targetFile.delete()
                                return@withContext Result.failure(
                                    IllegalArgumentException("File exceeds 25 MB offline transfer limit")
                                )
                            }
                            output.write(buffer, 0, read)
                        }
                        if (sizeBytes <= 0L) sizeBytes = total
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Cannot open selected file"))

                val validation = PacketValidator.validateFileMeta(displayName, mime, sizeBytes)
                if (validation.isFailure) {
                    targetFile.delete()
                    return@withContext Result.failure(validation.exceptionOrNull()!!)
                }

                Result.success(Triple(targetFile, mime, sizeBytes))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
