package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.local.ConversationType
import com.example.data.local.SosAlertEntity
import com.example.data.local.ThemeMode
import com.example.ui.components.LocalizedContextWrapper
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.BluetoothScreen
import com.example.ui.screens.ChatHistoryScreen
import com.example.ui.screens.CreateGroupScreen
import com.example.ui.screens.EmergencySosScreen
import com.example.ui.screens.FileSharingScreen
import com.example.ui.screens.GroupChatScreen
import com.example.ui.screens.GroupManagementScreen
import com.example.ui.screens.GroupPollsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.JoinGroupScreen
import com.example.ui.screens.NearbyDevicesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PrivateChatScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VoiceMessagesScreen
import com.example.ui.screens.WalkieTalkieScreen
import com.example.ui.screens.WifiHotspotScreen
import com.example.ui.state.MainViewModel
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.OfflineMessengerTheme
import kotlinx.coroutines.launch

private data class TopLevelTab(
    val route: AppRoute,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun OfflineMessengerRoot(viewModel: MainViewModel) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val isBengali = preferences.languageCode == "bn"

    LocalizedContextWrapper(languageCode = preferences.languageCode) {
        OfflineMessengerTheme(darkTheme = darkTheme, isBengali = isBengali) {
            OfflineMessengerAppShell(viewModel = viewModel)
        }
    }
}

@Composable
private fun OfflineMessengerAppShell(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()

    var activeSosBanner by remember { mutableStateOf<SosAlertEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sosBannerEvents.collect { alert ->
            activeSosBanner = alert
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val topTabs = listOf(
        TopLevelTab(AppRoute.HomeDashboard, R.string.nav_home, Icons.Default.Home, "nav_home"),
        TopLevelTab(AppRoute.NearbyDevices, R.string.nav_nearby, Icons.Default.Sensors, "nav_nearby"),
        TopLevelTab(AppRoute.WalkieTalkie(), R.string.nav_ptt, Icons.Default.RecordVoiceOver, "nav_ptt"),
        TopLevelTab(AppRoute.ChatHistory, R.string.nav_history, Icons.Default.History, "nav_history"),
        TopLevelTab(AppRoute.Settings, R.string.nav_settings, Icons.Default.Settings, "nav_settings")
    )

    val showTopNav = currentDest?.let { dest ->
        dest.hasRoute<AppRoute.HomeDashboard>() ||
            dest.hasRoute<AppRoute.NearbyDevices>() ||
            dest.hasRoute<AppRoute.WalkieTalkie>() ||
            dest.hasRoute<AppRoute.ChatHistory>() ||
            dest.hasRoute<AppRoute.Settings>()
    } ?: false

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopNav && !useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(AppRoute.HomeDashboard) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopNav && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight().statusBarsPadding()) {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(AppRoute.HomeDashboard) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Splash
                    ) {
                        composable<AppRoute.Splash> {
                            SplashScreen(
                                preferences = preferences,
                                onNavigateOnboarding = {
                                    navController.navigate(AppRoute.Onboarding) {
                                        popUpTo<AppRoute.Splash> { inclusive = true }
                                    }
                                },
                                onNavigateProfileSetup = {
                                    navController.navigate(AppRoute.ProfileSetup) {
                                        popUpTo<AppRoute.Splash> { inclusive = true }
                                    }
                                },
                                onNavigateHome = {
                                    navController.navigate(AppRoute.HomeDashboard) {
                                        popUpTo<AppRoute.Splash> { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<AppRoute.Onboarding> {
                            OnboardingScreen(
                                onFinished = {
                                    viewModel.markOnboardingDone()
                                    navController.navigate(AppRoute.ProfileSetup) {
                                        popUpTo<AppRoute.Onboarding> { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<AppRoute.ProfileSetup> {
                            ProfileSetupScreen(
                                preferences = preferences,
                                onLanguageChange = viewModel::setLanguage,
                                onSaveProfile = { name, bio, colorIdx, lang ->
                                    viewModel.saveProfile(name, bio, colorIdx, lang)
                                    navController.navigate(AppRoute.HomeDashboard) {
                                        popUpTo<AppRoute.ProfileSetup> { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<AppRoute.HomeDashboard> {
                            HomeDashboardScreen(
                                preferences = preferences,
                                conversations = conversations,
                                connectedPeers = connectedPeers,
                                onCreatePrivateChat = { title ->
                                    scope.launch {
                                        val (conv, _) = viewModel.createPrivateRoom(title)
                                        navController.navigate(AppRoute.PrivateChat(conv.id))
                                    }
                                },
                                onOpenConversation = { conv ->
                                    if (conv.type == ConversationType.GROUP) {
                                        navController.navigate(AppRoute.GroupChat(conv.id))
                                    } else {
                                        navController.navigate(AppRoute.PrivateChat(conv.id))
                                    }
                                },
                                onOpenCreateGroup = { navController.navigate(AppRoute.CreateGroup) },
                                onOpenJoinCode = { navController.navigate(AppRoute.JoinGroup) },
                                onOpenNearby = { navController.navigate(AppRoute.NearbyDevices) },
                                onOpenWifi = { navController.navigate(AppRoute.WifiHotspot) },
                                onOpenBluetooth = { navController.navigate(AppRoute.Bluetooth) },
                                onOpenWalkieTalkie = { navController.navigate(AppRoute.WalkieTalkie()) },
                                onOpenFileSharing = { navController.navigate(AppRoute.FileSharing()) },
                                onOpenVoiceMessages = { navController.navigate(AppRoute.VoiceMessages()) },
                                onOpenPolls = { navController.navigate(AppRoute.GroupPolls()) },
                                onOpenSos = { navController.navigate(AppRoute.EmergencySos()) },
                                onOpenAbout = { navController.navigate(AppRoute.About) }
                            )
                        }

                        composable<AppRoute.NearbyDevices> {
                            val status by viewModel.nearbyStatus.collectAsStateWithLifecycle()
                            val discovered by viewModel.nearbyDiscovered.collectAsStateWithLifecycle()
                            val pending by viewModel.nearbyPending.collectAsStateWithLifecycle()
                            val connectedRaw by viewModel.nearbyConnectedRaw.collectAsStateWithLifecycle()

                            NearbyDevicesScreen(
                                status = status,
                                discoveredPeers = discovered,
                                pendingRequests = pending,
                                connectedRaw = connectedRaw,
                                conversations = conversations,
                                onStartAdvertise = viewModel::startNearbyAdvertise,
                                onStartDiscover = viewModel::startNearbyDiscover,
                                onStop = viewModel::stopNearby,
                                onRequestConnect = viewModel::requestNearbyConnect,
                                onAccept = viewModel::acceptNearby,
                                onReject = viewModel::rejectNearby,
                                onDisconnect = { viewModel.disconnectPeer(com.example.data.local.TransportKind.NEARBY_CONNECTIONS, it) },
                                onPairEndpointWithRoom = { epId, room ->
                                    val pin = viewModel.getPairingPinForRoom(room.id)
                                    viewModel.pairConnectedEndpoint(
                                        com.example.data.local.TransportKind.NEARBY_CONNECTIONS,
                                        epId,
                                        room.roomCode,
                                        pin,
                                        room.type,
                                        room.title
                                    )
                                }
                            )
                        }

                        composable<AppRoute.PrivateChat> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.PrivateChat>()
                            val conv by viewModel.observeConversation(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val msgs by viewModel.observeMessages(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
                            val pin by produceState(initialValue = "------", route.conversationId) {
                                value = viewModel.getPairingPinForRoom(route.conversationId)
                            }
                            LaunchedEffect(route.conversationId) {
                                viewModel.clearUnread(route.conversationId)
                            }

                            PrivateChatScreen(
                                conversation = conv,
                                pairingPin = pin,
                                messages = msgs,
                                connectedPeers = connectedPeers,
                                voiceState = voiceState,
                                onSendMessage = { viewModel.sendMessage(route.conversationId, it) },
                                onShareUri = { viewModel.shareSafFile(route.conversationId, it) },
                                onStartVoiceRecord = viewModel::startVoiceRecording,
                                onStopVoiceRecord = { viewModel.stopAndSendVoiceRecording(route.conversationId) },
                                onToggleVoicePlay = viewModel::toggleVoicePlayback,
                                onOpenWalkieTalkie = { navController.navigate(AppRoute.WalkieTalkie(route.conversationId)) },
                                onOpenNearby = { navController.navigate(AppRoute.NearbyDevices) },
                                onOpenSos = { navController.navigate(AppRoute.EmergencySos(route.conversationId)) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.GroupChat> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.GroupChat>()
                            val conv by viewModel.observeConversation(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val members by viewModel.observeMembers(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val msgs by viewModel.observeMessages(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
                            val pin by produceState(initialValue = "------", route.conversationId) {
                                value = viewModel.getPairingPinForRoom(route.conversationId)
                            }
                            LaunchedEffect(route.conversationId) {
                                viewModel.clearUnread(route.conversationId)
                            }

                            GroupChatScreen(
                                conversation = conv,
                                pairingPin = pin,
                                members = members,
                                messages = msgs,
                                connectedPeers = connectedPeers,
                                voiceState = voiceState,
                                onSendMessage = { viewModel.sendMessage(route.conversationId, it) },
                                onShareUri = { viewModel.shareSafFile(route.conversationId, it) },
                                onStartVoiceRecord = viewModel::startVoiceRecording,
                                onStopVoiceRecord = { viewModel.stopAndSendVoiceRecording(route.conversationId) },
                                onToggleVoicePlay = viewModel::toggleVoicePlayback,
                                onOpenWalkieTalkie = { navController.navigate(AppRoute.WalkieTalkie(route.conversationId)) },
                                onOpenPolls = { navController.navigate(AppRoute.GroupPolls(route.conversationId)) },
                                onOpenGroupManagement = { navController.navigate(AppRoute.GroupManagement(route.conversationId)) },
                                onOpenNearby = { navController.navigate(AppRoute.NearbyDevices) },
                                onOpenSos = { navController.navigate(AppRoute.EmergencySos(route.conversationId)) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.CreateGroup> {
                            CreateGroupScreen(
                                defaultTransport = preferences.preferredTransport,
                                onCreateGroup = { title, desc, transport ->
                                    scope.launch {
                                        val (conv, _) = viewModel.createGroupRoom(title, desc, transport)
                                        navController.navigate(AppRoute.GroupChat(conv.id)) {
                                            popUpTo<AppRoute.CreateGroup> { inclusive = true }
                                        }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.JoinGroup> {
                            JoinGroupScreen(
                                defaultTransport = preferences.preferredTransport,
                                onJoin = { code, pin, transport, manualIp, isGroup ->
                                    scope.launch {
                                        val conv = viewModel.joinWithCode(code, pin, transport, manualIp, isGroup)
                                        val target = if (conv.type == ConversationType.GROUP) {
                                            AppRoute.GroupChat(conv.id)
                                        } else {
                                            AppRoute.PrivateChat(conv.id)
                                        }
                                        navController.navigate(target) {
                                            popUpTo<AppRoute.JoinGroup> { inclusive = true }
                                        }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.WifiHotspot> {
                            val status by viewModel.wifiStatus.collectAsStateWithLifecycle()
                            val discovered by viewModel.wifiDiscovered.collectAsStateWithLifecycle()
                            val connectedRaw by viewModel.wifiConnectedRaw.collectAsStateWithLifecycle()

                            WifiHotspotScreen(
                                status = status,
                                discoveredPeers = discovered,
                                connectedRaw = connectedRaw,
                                conversations = conversations,
                                onRefreshIp = { viewModel.refreshWifiIps() },
                                onStartServer = viewModel::startWifiServer,
                                onStopServer = viewModel::stopWifiServer,
                                onStartNsdScan = viewModel::startWifiNsdScan,
                                onStopNsdScan = viewModel::stopWifiNsdScan,
                                onConnectManual = viewModel::connectWifiHost,
                                onDisconnect = { viewModel.disconnectPeer(com.example.data.local.TransportKind.WIFI_LAN_HOTSPOT, it) },
                                onPairEndpointWithRoom = { epId, room ->
                                    val pin = viewModel.getPairingPinForRoom(room.id)
                                    viewModel.pairConnectedEndpoint(
                                        com.example.data.local.TransportKind.WIFI_LAN_HOTSPOT,
                                        epId,
                                        room.roomCode,
                                        pin,
                                        room.type,
                                        room.title
                                    )
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.Bluetooth> {
                            val status by viewModel.btStatus.collectAsStateWithLifecycle()
                            val paired by viewModel.btPaired.collectAsStateWithLifecycle()
                            val discovered by viewModel.btDiscovered.collectAsStateWithLifecycle()
                            val connectedRaw by viewModel.btConnectedRaw.collectAsStateWithLifecycle()

                            BluetoothScreen(
                                status = status,
                                pairedPeers = paired,
                                discoveredPeers = discovered,
                                connectedRaw = connectedRaw,
                                conversations = conversations,
                                onRefreshPaired = viewModel::refreshBtPaired,
                                onStartServer = viewModel::startBtServer,
                                onStopServer = viewModel::stopBtServer,
                                onStartDiscovery = viewModel::startBtDiscovery,
                                onStopDiscovery = viewModel::stopBtDiscovery,
                                onConnectDevice = viewModel::connectBtDevice,
                                onDisconnect = { viewModel.disconnectPeer(com.example.data.local.TransportKind.BLUETOOTH_CLASSIC, it) },
                                onPairEndpointWithRoom = { epId, room ->
                                    val pin = viewModel.getPairingPinForRoom(room.id)
                                    viewModel.pairConnectedEndpoint(
                                        com.example.data.local.TransportKind.BLUETOOTH_CLASSIC,
                                        epId,
                                        room.roomCode,
                                        pin,
                                        room.type,
                                        room.title
                                    )
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.WalkieTalkie> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.WalkieTalkie>()
                            val pttState by viewModel.pttState.collectAsStateWithLifecycle()
                            val audioLevel by viewModel.pttAudioLevel.collectAsStateWithLifecycle()
                            val speaker by viewModel.pttActiveSpeaker.collectAsStateWithLifecycle()

                            WalkieTalkieScreen(
                                initialConversationId = route.conversationId,
                                conversations = conversations,
                                pttState = pttState,
                                audioLevel = audioLevel,
                                activeSpeaker = speaker,
                                onStartTransmit = viewModel::startWalkieTalkie,
                                onStopTransmit = viewModel::stopWalkieTalkie,
                                onBack = if (route.conversationId != null) ({ navController.popBackStack() }) else null
                            )
                        }

                        composable<AppRoute.FileSharing> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.FileSharing>()
                            val transfers by viewModel.fileTransfers.collectAsStateWithLifecycle()
                            val fileMessages by viewModel.fileMessages.collectAsStateWithLifecycle()

                            FileSharingScreen(
                                initialConversationId = route.conversationId,
                                conversations = conversations,
                                transfers = transfers,
                                fileMessages = fileMessages,
                                onShareUri = viewModel::shareSafFile,
                                onDeleteMessage = viewModel::deleteMessage,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.VoiceMessages> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.VoiceMessages>()
                            val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
                            val voiceMsgs by viewModel.voiceMessages.collectAsStateWithLifecycle()

                            VoiceMessagesScreen(
                                initialConversationId = route.conversationId,
                                conversations = conversations,
                                voiceState = voiceState,
                                voiceMessages = voiceMsgs,
                                onStartRecord = viewModel::startVoiceRecording,
                                onStopAndSend = viewModel::stopAndSendVoiceRecording,
                                onCancelRecord = viewModel::cancelVoiceRecording,
                                onTogglePlay = viewModel::toggleVoicePlayback,
                                onDeleteMessage = viewModel::deleteMessage,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.GroupPolls> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.GroupPolls>()
                            val polls by viewModel.allPolls.collectAsStateWithLifecycle()
                            val votes by viewModel.allVotes.collectAsStateWithLifecycle()

                            GroupPollsScreen(
                                initialConversationId = route.conversationId,
                                conversations = conversations,
                                polls = polls,
                                votes = votes,
                                localDeviceId = preferences.deviceId,
                                onCreatePoll = viewModel::createPoll,
                                onVote = viewModel::votePoll,
                                onClosePoll = viewModel::closePoll,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.EmergencySos> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.EmergencySos>()
                            val alerts by viewModel.sosAlerts.collectAsStateWithLifecycle()

                            EmergencySosScreen(
                                initialConversationId = route.conversationId,
                                alerts = alerts,
                                connectedPeerCount = connectedPeers.size,
                                onTriggerSos = viewModel::triggerEmergencySos,
                                onAcknowledge = viewModel::acknowledgeSos,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.ChatHistory> {
                            val query by viewModel.searchQuery.collectAsStateWithLifecycle()
                            val msgs by viewModel.historyMessages.collectAsStateWithLifecycle()

                            ChatHistoryScreen(
                                searchQuery = query,
                                messages = msgs,
                                conversations = conversations,
                                onSearchChange = viewModel::setHistorySearchQuery,
                                onOpenConversation = { conv ->
                                    if (conv.type == ConversationType.GROUP) {
                                        navController.navigate(AppRoute.GroupChat(conv.id))
                                    } else {
                                        navController.navigate(AppRoute.PrivateChat(conv.id))
                                    }
                                },
                                onDeleteMessage = viewModel::deleteMessage,
                                onDeleteConversation = viewModel::deleteConversation
                            )
                        }

                        composable<AppRoute.GroupManagement> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.GroupManagement>()
                            val conv by viewModel.observeConversation(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val members by viewModel.observeMembers(route.conversationId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val pin by produceState(initialValue = "------", route.conversationId) {
                                value = viewModel.getPairingPinForRoom(route.conversationId)
                            }

                            GroupManagementScreen(
                                conversation = conv,
                                pairingPin = pin,
                                members = members,
                                localDeviceId = preferences.deviceId,
                                onUpdateDetails = { t, d -> viewModel.updateGroupDetails(route.conversationId, t, d) },
                                onKickMember = { devId -> viewModel.kickMember(route.conversationId, devId) },
                                onDeleteGroup = {
                                    viewModel.deleteConversation(route.conversationId)
                                    navController.popBackStack(AppRoute.HomeDashboard, inclusive = false)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AppRoute.Settings> {
                            SettingsScreen(
                                preferences = preferences,
                                onSaveProfile = viewModel::saveProfile,
                                onSetLanguage = viewModel::setLanguage,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetTransport = viewModel::setPreferredTransport,
                                onClearData = viewModel::clearAllLocalData,
                                onOpenAbout = { navController.navigate(AppRoute.About) }
                            )
                        }

                        composable<AppRoute.About> {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    // Live incoming Emergency SOS alert overlay banner
                    activeSosBanner?.let { alert ->
                        Surface(
                            color = EmergencyCrimson,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .align(Alignment.TopCenter)
                                .clickable {
                                    activeSosBanner = null
                                    navController.navigate(AppRoute.EmergencySos(alert.conversationId))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Sos, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SOS from ${alert.senderName}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                                    Text(alert.message, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                TextButton(onClick = { activeSosBanner = null }) {
                                    Text("Dismiss", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
