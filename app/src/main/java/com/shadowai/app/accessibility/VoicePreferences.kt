package com.shadowai.app.accessibility

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "voice_settings")

/**
 * DataStore preferences for voice recognition settings.
 * Persists hotword settings across app restarts.
 */
@Singleton
class VoicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val HOTWORD_ENABLED = booleanPreferencesKey("hotword_enabled")
        val CUSTOM_HOTWORD_PHRASE = stringPreferencesKey("custom_hotword_phrase")
        val HOTWORD_SENSITIVITY = floatPreferencesKey("hotword_sensitivity")
        val RECORD_AUDIO_PERMISSION_REQUESTED = booleanPreferencesKey("record_audio_permission_requested")
    }

    /**
     * Whether hotword detection is enabled (default: false)
     */
    val hotwordEnabled: Flow<Boolean> = context.voiceDataStore.data
        .map { it[PreferencesKeys.HOTWORD_ENABLED] ?: false }

    /**
     * Custom hotword phrase (default: empty string, uses default "Hey Shadow")
     */
    val customHotwordPhrase: Flow<String> = context.voiceDataStore.data
        .map { it[PreferencesKeys.CUSTOM_HOTWORD_PHRASE] ?: "" }

    /**
     * Hotword sensitivity threshold (default: 0.7)
     */
    val hotwordSensitivity: Flow<Float> = context.voiceDataStore.data
        .map { it[PreferencesKeys.HOTWORD_SENSITIVITY] ?: 0.7f }

    /**
     * Whether RECORD_AUDIO permission has been requested at least once
     */
    val recordAudioPermissionRequested: Flow<Boolean> = context.voiceDataStore.data
        .map { it[PreferencesKeys.RECORD_AUDIO_PERMISSION_REQUESTED] ?: false }

    /**
     * Save hotword enabled state
     */
    suspend fun saveHotwordEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { it[PreferencesKeys.HOTWORD_ENABLED] = enabled }
    }

    /**
     * Save custom hotword phrase
     */
    suspend fun saveCustomHotwordPhrase(phrase: String) {
        context.voiceDataStore.edit { it[PreferencesKeys.CUSTOM_HOTWORD_PHRASE] = phrase }
    }

    /**
     * Save hotword sensitivity threshold
     */
    suspend fun saveHotwordSensitivity(sensitivity: Float) {
        context.voiceDataStore.edit { 
            it[PreferencesKeys.HOTWORD_SENSITIVITY] = sensitivity.coerceIn(0.0f, 1.0f) 
        }
    }

    /**
     * Mark that RECORD_AUDIO permission has been requested
     */
    suspend fun markRecordAudioPermissionRequested() {
        context.voiceDataStore.edit { it[PreferencesKeys.RECORD_AUDIO_PERMISSION_REQUESTED] = true }
    }
}
