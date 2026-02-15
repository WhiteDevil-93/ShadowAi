package com.shadowai.app.auth

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.shadowai.app.BuildConfig
import com.shadowai.app.security.SecureDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manages user authentication state persistence using DataStore.
 *
 * CRITICAL FIX: Sensitive user data now encrypted at rest using SecureDataStore.
 * Non-sensitive preferences remain in standard DataStore for performance.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val secureDataStore: SecureDataStore
) {
    private val dataStore = context.dataStore

    companion object {
        private const val TAG = "UserPreferences"

        // Standard (non-sensitive) preference keys
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val LAST_SIGN_IN_TIME = longPreferencesKey("last_sign_in_time")
        private val THEME_OPTION = stringPreferencesKey("theme_option")
        private val FONT_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("font_scale")
        private val INFERENCE_ISOLATION_ENABLED = booleanPreferencesKey("inference_isolation_enabled")
        private val NNAPI_DELEGATION_ENABLED = booleanPreferencesKey("nnapi_delegation_enabled")
        private val MEMORY_MAPPING_ENABLED = booleanPreferencesKey("memory_mapping_enabled")
        private val AUTO_SUMMARIZATION_ENABLED = booleanPreferencesKey("auto_summarization_enabled")

        // Encrypted (sensitive) data keys
        private const val SECURE_USER_DATA_KEY = "user_data_encrypted"
    }

    /**
     * Flow that emits the current authentication state
     */
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { preferences: Preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    /**
     * Flow that emits the current user data (decrypted from secure storage)
     */
    val currentUser: Flow<User?> = kotlinx.coroutines.flow.flow {
        try {
            val encryptedData = secureDataStore.getString(SECURE_USER_DATA_KEY)
            encryptedData?.let { json ->
                try {
                    val user = gson.fromJson(json, User::class.java)
                    emit(user)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to decrypt user data", e)
                    emit(null)
                }
            } ?: emit(null)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to load user data from secure storage", e)
            emit(null)
        }
    }

    /**
     * Saves user authentication state with encryption for sensitive data
     */
    suspend fun saveUser(user: User) {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[LAST_SIGN_IN_TIME] = System.currentTimeMillis()
        }

        // Encrypt and store sensitive user data
        try {
            val userDataJson = gson.toJson(user)
            secureDataStore.putString(SECURE_USER_DATA_KEY, userDataJson)
            Log.d(TAG, "User data saved securely")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user data securely", e)
            throw e
        }
    }

    /**
     * Clears user authentication state
     */
    suspend fun clearUser() {
        dataStore.edit { preferences ->
            preferences.remove(IS_LOGGED_IN)
            preferences.remove(LAST_SIGN_IN_TIME)
        }

        // Remove encrypted user data
        try {
            secureDataStore.remove(SECURE_USER_DATA_KEY)
            Log.d(TAG, "User data cleared securely")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear user data", e)
        }
    }

    /**
     * Gets the current user as a suspend function.
     * Prefer this over the blocking variant.
     */
    suspend fun getCurrentUserSuspend(): User? = currentUser.first()

    /**
     * Checks if user is logged in as a suspend function.
     * Prefer this over the blocking variant.
     */
    suspend fun isLoggedInSuspend(): Boolean = isLoggedIn.first()

    /**
     * Gets the last sign-in time
     */
    val lastSignInTime: Flow<Long?> = dataStore.data.map { preferences: Preferences ->
        preferences[LAST_SIGN_IN_TIME]
    }

    /**
     * Flow that emits the current theme option (default: SYSTEM)
     */
    val themeOption: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_OPTION] ?: "SYSTEM"
    }

    /**
     * Flow that emits the current font scale (default: 1.0f)
     */
    val fontScale: Flow<Float> = dataStore.data.map { preferences ->
        preferences[FONT_SCALE] ?: 1.0f
    }

    /**
     * Flow that emits whether isolated inference process should be used.
     *
     * Defaults to the build-time setting when user has not explicitly chosen yet.
     */
    val inferenceIsolationEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[INFERENCE_ISOLATION_ENABLED] ?: BuildConfig.USE_ISOLATED_INFERENCE_ENGINE
    }

    /**
     * Flow that emits whether NNAPI delegation should be used.
     *
     * Auto-enabled if device supports NNAPI and user hasn't explicitly chosen.
     */
    val nnapiDelegationEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        // Note: This will be initialized by DeviceCapabilities on first launch
        // Default to false until explicitly checked
        preferences[NNAPI_DELEGATION_ENABLED] ?: false
    }

    /**
     * Flow that emits whether memory-mapped model loading should be used.
     *
     * Defaults to true as it allows running larger models.
     */
    val memoryMappingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[MEMORY_MAPPING_ENABLED] ?: true
    }

    /**
     * Flow that emits whether auto-summarization should be used.
     *
     * Defaults to true to manage context window efficiently.
     */
    val autoSummarizationEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_SUMMARIZATION_ENABLED] ?: true
    }

    suspend fun saveThemeOption(option: String) {
        dataStore.edit { preferences ->
            preferences[THEME_OPTION] = option
        }
    }

    suspend fun saveFontScale(scale: Float) {
        dataStore.edit { preferences ->
            preferences[FONT_SCALE] = scale
        }
    }

    suspend fun saveInferenceIsolationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[INFERENCE_ISOLATION_ENABLED] = enabled
        }
    }

    /**
     * Enable or disable NNAPI delegation.
     */
    suspend fun saveNnapiDelegationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NNAPI_DELEGATION_ENABLED] = enabled
        }
    }

    /**
     * Enable or disable memory-mapped model loading.
     */
    suspend fun saveMemoryMappingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[MEMORY_MAPPING_ENABLED] = enabled
        }
    }

    /**
     * Enable or disable auto-summarization.
     */
    suspend fun saveAutoSummarizationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_SUMMARIZATION_ENABLED] = enabled
        }
    }

    /**
     * Migrate existing plaintext user data to encrypted storage.
     * Run this once during app startup to migrate existing users.
     */
    suspend fun migrateToEncryptedStorage(): Boolean {
        return try {
            val oldData = dataStore.data.first()
            val oldUserDataJson = oldData[stringPreferencesKey("user_data")]

            if (oldUserDataJson != null) {
                // Check if already migrated
                val alreadyMigrated = secureDataStore.contains(SECURE_USER_DATA_KEY)

                if (!alreadyMigrated) {
                    Log.i(TAG, "Migrating user data to encrypted storage")

                    // Save to secure storage
                    secureDataStore.putString(SECURE_USER_DATA_KEY, oldUserDataJson)

                    // Remove from plaintext storage
                    dataStore.edit { preferences ->
                        preferences.remove(stringPreferencesKey("user_data"))
                    }

                    Log.i(TAG, "User data migration completed successfully")
                    true
                } else {
                    Log.d(TAG, "User data already migrated, skipping")
                    true
                }
            } else {
                Log.d(TAG, "No existing user data to migrate")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate user data", e)
            false
        }
    }
}
