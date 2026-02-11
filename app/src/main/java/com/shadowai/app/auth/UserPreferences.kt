package com.shadowai.app.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.shadowai.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manages user authentication state persistence using DataStore.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson = Gson()
) {
    private val dataStore = context.dataStore

    companion object {
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val USER_DATA = stringPreferencesKey("user_data")
        private val LAST_SIGN_IN_TIME = longPreferencesKey("last_sign_in_time")
        private val THEME_OPTION = stringPreferencesKey("theme_option")
        private val FONT_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("font_scale")
        private val INFERENCE_ISOLATION_ENABLED = booleanPreferencesKey("inference_isolation_enabled")
    }

    /**
     * Flow that emits the current authentication state
     */
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { preferences: Preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    /**
     * Flow that emits the current user data
     */
    val currentUser: Flow<User?> = dataStore.data.map { preferences: Preferences ->
        preferences[USER_DATA]?.let { json: String ->
            try {
                gson.fromJson(json, User::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Saves user authentication state
     */
    suspend fun saveUser(user: User) {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[USER_DATA] = gson.toJson(user)
            preferences[LAST_SIGN_IN_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * Clears user authentication state
     */
    suspend fun clearUser() {
        dataStore.edit { preferences ->
            preferences.remove(IS_LOGGED_IN)
            preferences.remove(USER_DATA)
            preferences.remove(LAST_SIGN_IN_TIME)
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
}
