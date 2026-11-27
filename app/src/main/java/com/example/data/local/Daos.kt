package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE roomCode = :roomCode LIMIT 1")
    suspend fun getByRoomCode(roomCode: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query(
        "UPDATE conversations SET lastMessagePreview = :preview, lastMessageAt = :timestamp, unreadCount = unreadCount + :unreadDelta WHERE id = :id"
    )
    suspend fun updateLastMessage(id: String, preview: String, timestamp: Long, unreadDelta: Int)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun clearUnread(id: String)

    @Query("UPDATE conversations SET title = :title, description = :description WHERE id = :id")
    suspend fun updateDetails(id: String, title: String, description: String)

    @Query("UPDATE conversations SET encryptedKeyBlob = :encryptedKeyBlob, safetyCode = :safetyCode WHERE id = :id")
    suspend fun updateSessionSecurity(id: String, encryptedKeyBlob: String, safetyCode: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE conversationId = :conversationId ORDER BY role ASC, joinedAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE conversationId = :conversationId")
    suspend fun getForConversation(conversationId: String): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: MemberEntity)

    @Query(
        "UPDATE members SET connectionStatus = :status, lastSeenAt = :lastSeenAt, transportLabel = :transportLabel WHERE conversationId = :conversationId AND deviceId = :deviceId"
    )
    suspend fun updateStatus(
        conversationId: String,
        deviceId: String,
        status: PeerConnectionStatus,
        lastSeenAt: Long,
        transportLabel: String
    )

    @Query("UPDATE members SET connectionStatus = :status WHERE deviceId = :deviceId")
    suspend fun updateStatusAcrossConversations(deviceId: String, status: PeerConnectionStatus)

    @Query("DELETE FROM members WHERE conversationId = :conversationId AND deviceId = :deviceId")
    suspend fun removeMember(conversationId: String, deviceId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 200")
    fun observeAllRecent(): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE content LIKE '%' || :query || '%' OR attachmentName LIKE '%' || :query || '%' OR senderName LIKE '%' || :query || '%' ORDER BY timestamp DESC"
    )
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = 'VOICE' ORDER BY timestamp DESC")
    fun observeVoiceMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = 'IMAGE' OR type = 'FILE' ORDER BY timestamp DESC")
    fun observeFileMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: String, status: DeliveryStatus)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

@Dao
interface PollDao {
    @Query("SELECT * FROM polls WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    fun observePolls(conversationId: String): Flow<List<PollEntity>>

    @Query("SELECT * FROM polls ORDER BY createdAt DESC")
    fun observeAllPolls(): Flow<List<PollEntity>>

    @Query("SELECT * FROM poll_votes WHERE pollId IN (SELECT id FROM polls WHERE conversationId = :conversationId)")
    fun observeVotesForConversation(conversationId: String): Flow<List<PollVoteEntity>>

    @Query("SELECT * FROM poll_votes")
    fun observeAllVotes(): Flow<List<PollVoteEntity>>

    @Query("SELECT * FROM polls WHERE id = :pollId LIMIT 1")
    suspend fun getPollById(pollId: String): PollEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoll(poll: PollEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: PollVoteEntity)

    @Query("DELETE FROM poll_votes WHERE pollId = :pollId AND voterId = :voterId")
    suspend fun deleteVotesByVoter(pollId: String, voterId: String)

    @Query("UPDATE polls SET isClosed = 1 WHERE id = :pollId")
    suspend fun closePoll(pollId: String)

    @Transaction
    suspend fun castVote(poll: PollEntity, vote: PollVoteEntity) {
        if (poll.isClosed) return
        if (!poll.allowMultiple) {
            deleteVotesByVoter(poll.id, vote.voterId)
        }
        insertVote(vote)
    }
}

@Dao
interface SosDao {
    @Query("SELECT * FROM sos_alerts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SosAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: SosAlertEntity)

    @Query("UPDATE sos_alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("DELETE FROM sos_alerts")
    suspend fun clearAll()
}
