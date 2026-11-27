package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ConversationType
import com.example.data.local.OfflineMessengerDatabase
import com.example.data.local.OfflineMessengerRepository
import com.example.data.local.TransportKind
import com.example.data.local.UserPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: OfflineMessengerDatabase
    private lateinit var repo: OfflineMessengerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            OfflineMessengerDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = OfflineMessengerRepository(
            context = context,
            db = db,
            preferencesStore = UserPreferencesDataStore(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches Offline Messenger`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Offline Messenger", appName)
    }

    @Test
    fun `create room persists encrypted room and prevents duplicate single choice poll votes`() = runBlocking {
        val conv = repo.createRoom(
            title = "Rescue Alpha",
            description = "Base channel",
            type = ConversationType.GROUP,
            hostDeviceId = "OM-HOST1",
            hostName = "Host One",
            transport = TransportKind.WIFI_LAN_HOTSPOT,
            isLocalHost = true
        )
        assertNotNull(repo.getRoomSecretKey(conv.id))

        val poll = repo.createPoll(
            conversationId = conv.id,
            question = "Next checkpoint?",
            options = listOf("North", "South"),
            creatorId = "OM-HOST1",
            creatorName = "Host One",
            allowMultiple = false
        )

        repo.castVote(poll.id, "OM-PEER1", "Peer 1", 0)
        repo.castVote(poll.id, "OM-PEER1", "Peer 1", 1)

        val votes = repo.observeVotes(conv.id).first()
        assertEquals(1, votes.size)
        assertEquals(1, votes.first().optionIndex)
    }

    @Test
    fun `local message search finds inserted message`() = runBlocking {
        val conv = repo.createRoom(
            title = "Private Test",
            description = "",
            type = ConversationType.PRIVATE,
            hostDeviceId = "OM-A",
            hostName = "Alice",
            transport = TransportKind.NEARBY_CONNECTIONS,
            isLocalHost = true
        )
        repo.insertMessage(
            com.example.data.local.MessageEntity(
                id = "msg-1",
                conversationId = conv.id,
                senderId = "OM-A",
                senderName = "Alice",
                type = com.example.data.local.MessageType.TEXT,
                content = "Meet at Sector 7 shelter"
            )
        )

        val hits = repo.searchMessages("Sector 7").first()
        assertTrue(hits.any { it.id == "msg-1" })
    }
}
