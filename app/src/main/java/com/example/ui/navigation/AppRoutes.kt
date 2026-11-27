package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Onboarding : AppRoute
    @Serializable data object ProfileSetup : AppRoute
    @Serializable data object HomeDashboard : AppRoute
    @Serializable data object NearbyDevices : AppRoute
    @Serializable data class PrivateChat(val conversationId: String) : AppRoute
    @Serializable data class GroupChat(val conversationId: String) : AppRoute
    @Serializable data object CreateGroup : AppRoute
    @Serializable data object JoinGroup : AppRoute
    @Serializable data object WifiHotspot : AppRoute
    @Serializable data object Bluetooth : AppRoute
    @Serializable data class WalkieTalkie(val conversationId: String? = null) : AppRoute
    @Serializable data class FileSharing(val conversationId: String? = null) : AppRoute
    @Serializable data class VoiceMessages(val conversationId: String? = null) : AppRoute
    @Serializable data class GroupPolls(val conversationId: String? = null) : AppRoute
    @Serializable data class EmergencySos(val conversationId: String? = null) : AppRoute
    @Serializable data object ChatHistory : AppRoute
    @Serializable data class GroupManagement(val conversationId: String) : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object About : AppRoute
}
