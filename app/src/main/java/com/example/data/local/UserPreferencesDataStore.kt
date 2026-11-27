package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "offline_messenger_prefs"
)

data class UserPreferences(
    val deviceId: String = "",
    val displayName: String = "",
    val statusBio: String = "",
    val avatarColorIndex: Int = 0,
    val languageCode: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val preferredTransport: TransportKind = TransportKind.NEARBY_CONNECTIONS,
    val onboardingCompleted: Boolean = false,
    val profileCompleted: Boolean = false
)

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val STATUS_BIO = stringPreferencesKey("status_bio")
        val AVATAR_COLOR_INDEX = intPreferencesKey("avatar_color_index")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PREFERRED_TRANSPORT = stringPreferencesKey("preferred_transport")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PROFILE_COMPLETED = booleanPreferencesKey("profile_completed")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { prefs ->
        UserPreferences(
            deviceId = prefs[Keys.DEVICE_ID] ?: "",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            statusBio = prefs[Keys.STATUS_BIO] ?: "",
            avatarColorIndex = prefs[Keys.AVATAR_COLOR_INDEX] ?: 0,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            preferredTransport = runCatching {
                TransportKind.valueOf(
                    prefs[Keys.PREFERRED_TRANSPORT] ?: TransportKind.NEARBY_CONNECTIONS.name
                )
            }.getOrDefault(TransportKind.NEARBY_CONNECTIONS),
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            profileCompleted = prefs[Keys.PROFILE_COMPLETED] ?: false
        )
    }

    suspend fun ensureDeviceId(): String {
        val current = context.userPreferencesDataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val generated = "OM-" + UUID.randomUUID().toString().take(8).uppercase()
        context.userPreferencesDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun markOnboardingCompleted() {
        ensureDeviceId()
        context.userPreferencesDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun saveProfile(
        displayName: String,
        statusBio: String,
        avatarColorIndex: Int,
        languageCode: String
    ) {
        ensureDeviceId()
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = displayName.trim()
            prefs[Keys.STATUS_BIO] = statusBio.trim()
            prefs[Keys.AVATAR_COLOR_INDEX] = avatarColorIndex
            prefs[Keys.LANGUAGE_CODE] = languageCode
            prefs[Keys.PROFILE_COMPLETED] = true
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun setLanguage(languageCode: String) {
        context.userPreferencesDataStore.edit { it[Keys.LANGUAGE_CODE] = languageCode }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPreferredTransport(transport: TransportKind) {
        context.userPreferencesDataStore.edit { it[Keys.PREFERRED_TRANSPORT] = transport.name }
    }
}
