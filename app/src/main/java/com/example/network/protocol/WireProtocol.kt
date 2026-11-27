package com.example.network.protocol

import kotlinx.serialization.Serializable

enum class PacketType {
    HANDSHAKE_INIT,
    HANDSHAKE_ACK,
    HANDSHAKE_CONFIRM,
    CHAT_MESSAGE,
    FILE_META,
    FILE_CHUNK,
    FILE_ACK,
    POLL_CREATE,
    POLL_VOTE,
    SOS_ALERT,
    PTT_AUDIO_FRAME,
    MEMBER_LIST_SYNC,
    MEMBER_KICK,
    DELIVERY_ACK,
    PING
}

@Serializable
data class WirePacket(
    val version: Int = 1,
    val packetType: PacketType,
    val roomId: String,
    val packetId: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val ivB64: String? = null,
    val payloadB64: String
)

@Serializable
data class HandshakeInitPayload(
    val roomCode: String,
    val ecPubKeyB64: String,
    val nonceB64: String,
    val conversationType: String,
    val title: String
)

@Serializable
data class HandshakeAckPayload(
    val roomCode: String,
    val ecPubKeyB64: String,
    val nonceB64: String,
    val macB64: String,
    val accepted: Boolean,
    val reason: String = "",
    val conversationTitle: String = "",
    val conversationType: String = "PRIVATE"
)

@Serializable
data class HandshakeConfirmPayload(
    val roomCode: String,
    val macB64: String
)

@Serializable
data class ChatPayload(
    val messageId: String,
    val messageType: String,
    val text: String,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val attachmentSize: Long = 0L,
    val voiceDurationMs: Long = 0L
)

@Serializable
data class FileMetaPayload(
    val transferId: String,
    val messageId: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val totalChunks: Int,
    val sha256Hex: String,
    val isVoiceNote: Boolean = false,
    val voiceDurationMs: Long = 0L
)

@Serializable
data class FileChunkPayload(
    val transferId: String,
    val chunkIndex: Int,
    val dataB64: String
)

@Serializable
data class FileAckPayload(
    val transferId: String,
    val completed: Boolean,
    val reason: String = ""
)

@Serializable
data class PollCreatePayload(
    val pollId: String,
    val question: String,
    val options: List<String>,
    val allowMultiple: Boolean
)

@Serializable
data class PollVotePayload(
    val pollId: String,
    val optionIndex: Int
)

@Serializable
data class SosPayload(
    val alertId: String,
    val message: String,
    val transportLabel: String
)

@Serializable
data class PttFramePayload(
    val sequence: Long,
    val pcmB64: String
)

@Serializable
data class MemberSyncItem(
    val deviceId: String,
    val displayName: String,
    val role: String,
    val status: String
)

@Serializable
data class MemberSyncPayload(
    val members: List<MemberSyncItem>
)

@Serializable
data class KickPayload(
    val targetDeviceId: String,
    val reason: String = "Removed by group owner"
)

@Serializable
data class DeliveryAckPayload(
    val messageId: String
)
