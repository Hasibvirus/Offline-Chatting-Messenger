package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ConversationType { PRIVATE, GROUP }
enum class MemberRole { OWNER, MEMBER }
enum class PeerConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED }
enum class MessageType { TEXT, IMAGE, FILE, VOICE, POLL, SOS, SYSTEM }
enum class DeliveryStatus { SENDING, SENT, DELIVERED, FAILED }
enum class TransportKind { NEARBY_CONNECTIONS, WIFI_LAN_HOTSPOT, BLUETOOTH_CLASSIC }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["roomCode"], unique = true)]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val type: ConversationType,
    val roomCode: String,
    val pairingPinHash: String,
    val encryptedPinBlob: String = "",
    val encryptedKeyBlob: String,
    val safetyCode: String,
    val hostDeviceId: String,
    val isLocalHost: Boolean,
    val preferredTransport: TransportKind = TransportKind.NEARBY_CONNECTIONS,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessagePreview: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
)

@Entity(
    tableName = "members",
    primaryKeys = ["conversationId", "deviceId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MemberEntity(
    val conversationId: String,
    val deviceId: String,
    val displayName: String,
    val role: MemberRole = MemberRole.MEMBER,
    val connectionStatus: PeerConnectionStatus = PeerConnectionStatus.DISCONNECTED,
    val transportLabel: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val type: MessageType,
    val content: String,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val attachmentSize: Long = 0L,
    val voiceDurationMs: Long = 0L,
    val pollId: String? = null,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = false,
    val isEmergency: Boolean = false
)

@Entity(
    tableName = "polls",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class PollEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val question: String,
    val optionsJson: String,
    val createdById: String,
    val createdByName: String,
    val allowMultiple: Boolean = false,
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "poll_votes",
    primaryKeys = ["pollId", "voterId", "optionIndex"],
    foreignKeys = [
        ForeignKey(
            entity = PollEntity::class,
            parentColumns = ["id"],
            childColumns = ["pollId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pollId"])]
)
data class PollVoteEntity(
    val pollId: String,
    val voterId: String,
    val voterName: String,
    val optionIndex: Int,
    val votedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sos_alerts",
    indices = [Index(value = ["timestamp"])]
)
data class SosAlertEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val transportUsed: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false
)
