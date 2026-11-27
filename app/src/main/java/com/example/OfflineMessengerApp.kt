package com.example

import android.app.Application
import com.example.data.local.OfflineMessengerDatabase
import com.example.data.local.OfflineMessengerRepository
import com.example.data.local.UserPreferencesDataStore
import com.example.network.audio.VoiceRecorderPlayer
import com.example.network.audio.WalkieTalkieEngine
import com.example.network.session.MeshSessionManager
import com.example.network.transport.BluetoothClassicTransport
import com.example.network.transport.NearbyConnectionsTransport
import com.example.network.transport.WifiLanTransport

class AppContainer(application: Application) {
    val database: OfflineMessengerDatabase by lazy {
        OfflineMessengerDatabase.create(application)
    }
    val preferencesStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(application)
    }
    val repository: OfflineMessengerRepository by lazy {
        OfflineMessengerRepository(application, database, preferencesStore)
    }
    val nearbyTransport: NearbyConnectionsTransport by lazy {
        NearbyConnectionsTransport(application)
    }
    val wifiTransport: WifiLanTransport by lazy {
        WifiLanTransport(application)
    }
    val bluetoothTransport: BluetoothClassicTransport by lazy {
        BluetoothClassicTransport(application)
    }
    val walkieEngine: WalkieTalkieEngine by lazy {
        WalkieTalkieEngine(application)
    }
    val voiceRecorderPlayer: VoiceRecorderPlayer by lazy {
        VoiceRecorderPlayer(application)
    }
    val sessionManager: MeshSessionManager by lazy {
        MeshSessionManager(
            context = application,
            repository = repository,
            nearbyTransport = nearbyTransport,
            wifiTransport = wifiTransport,
            bluetoothTransport = bluetoothTransport,
            walkieEngine = walkieEngine
        )
    }
}

class OfflineMessengerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
