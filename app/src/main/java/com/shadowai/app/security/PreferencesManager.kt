package com.shadowai.app.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val REQUIRE_BIOMETRIC_FOR_DOWNLOADS = booleanPreferencesKey("require_biometric_for_downloads")
        val REQUIRE_BIOMETRIC_FOR_HISTORY = booleanPreferencesKey("require_biometric_for_history")
        val REQUIRE_BIOMETRIC_FOR_SETTINGS = booleanPreferencesKey("require_biometric_for_settings")
        val PREVENT_SCREENSHOTS = booleanPreferencesKey("prevent_screenshots")
        val AUTO_LOCK_ENABLED = booleanPreferencesKey("auto_lock_enabled")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
    }

    // Biometric settings
    val requireBiometricForDownloads: Flow<Boolean> = context.securityDataStore.data
        .map { it[PreferencesKeys.REQUIRE_BIOMETRIC_FOR_DOWNLOADS] ?: false }

    val requireBiometricForHistory: Flow<Boolean> = context.securityDataStore.data
        .map { it[PreferencesKeys.REQUIRE_BIOMETRIC_FOR_HISTORY] ?: false }

    val requireBiometricForSettings: Flow<Boolean> = context.securityDataStore.data
        .map { it[PreferencesKeys.REQUIRE_BIOMETRIC_FOR_SETTINGS] ?: false }

    // Screenshot protection
    val preventScreenshots: Flow<Boolean> = context.securityDataStore.data
        .map { it[PreferencesKeys.PREVENT_SCREENSHOTS] ?: true }

    // Auto-lock settings
    val autoLockEnabled: Flow<Boolean> = context.securityDataStore.data
        .map { it[PreferencesKeys.AUTO_LOCK_ENABLED] ?: false }

    val autoLockTimeout: Flow<com.shadowai.app.ui.settings.SecuritySettingsViewModel.AutoLockTimeout> =
        context.securityDataStore.data
            .map { prefs ->
                val timeoutKey = prefs[PreferencesKeys.AUTO_LOCK_TIMEOUT]
                timeoutKey?.let { key ->
                    com.shadowai.app.ui.settings.SecuritySettingsViewModel.AutoLockTimeout.entries.firstOrNull { 
                        it.name == key 
                    } ?: com.shadowai.app.ui.settings.SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES
                } ?: com.shadowai.app.ui.settings.SecuritySettingsViewModel.AutoLockTimeout.FIVE_MINUTES
            }

    suspend fun saveRequireBiometricForDownloads(require: Boolean) {
        context.securityDataStore.edit { it[PreferencesKeys.REQUIRE_BIOMETRIC_FOR_DOWNLOADS] = require }
    }

    suspend fun saveRequireBiometricForHistory(require: Boolean) {
        context.securityDataStore.edit { it[PreferencesKeys.REQUIRE_BIOMETRIC_FOR_HISTORY] = require }
    }

    suspend fun saveRequireBiometricForSettings(require: Boolean) {
        context.securityDataStore.edit { it[PreferencesKeys.REQUIRE_BIOMETRIC_FOR_SETTINGS] = require }
    }

    suspend fun savePreventScreenshots(prevent: Boolean) {
        context.securityDataStore.edit { it[PreferencesKeys.PREVENT_SCREENSHOTS] = prevent }
    }

    suspend fun saveAutoLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[PreferencesKeys.AUTO_LOCK_ENABLED] = enabled }
    }

    suspend fun saveAutoLockTimeout(timeout: com.shadowai.app.ui.settings.SecuritySettingsViewModel.AutoLockTimeout) {
        context.securityDataStore.edit { it[PreferencesKeys.AUTO_LOCK_TIMEOUT] = timeout.name }
    }
}