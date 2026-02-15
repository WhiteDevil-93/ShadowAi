package com.shadowai.app.auth

import android.util.Log
import com.shadowai.app.security.SecureDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Manages JWT token refresh lifecycle for cloud API providers.
 */
@Singleton
class TokenRefreshManager @Inject constructor(
    private val secureDataStore: SecureDataStore,
    private val gson: Gson
) {

    private companion object {
        private const val TAG = "TokenRefresh"
        private const val REFRESH_BUFFER_MINUTES = 5L
        private const val MIN_REFRESH_INTERVAL_SECONDS = 60L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val TOKEN_DATA_PREFIX = "token_data_"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val refreshJobs = ConcurrentHashMap<String, Job>()

    private val _tokenRefreshEvents = MutableStateFlow<TokenRefreshEvent?>(null)
    val tokenRefreshEvents: StateFlow<TokenRefreshEvent?> = _tokenRefreshEvents.asStateFlow()

    data class TokenInfo(
        val providerId: String,
        val token: String,
        val expiresAt: Long,
        val refreshCallback: suspend (String) -> Result<String>,
        var lastRefreshAttempt: Long = 0,
        var refreshAttempts: Int = 0
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
        fun isNearExpiry(bufferMinutes: Long = REFRESH_BUFFER_MINUTES): Boolean {
            return System.currentTimeMillis() >= (expiresAt - bufferMinutes.minutes.inWholeMilliseconds)
        }
        fun canRefresh(): Boolean {
            return System.currentTimeMillis() - lastRefreshAttempt >= MIN_REFRESH_INTERVAL_SECONDS.seconds.inWholeMilliseconds
        }
    }

    private data class PersistentTokenData(
        val providerId: String,
        val token: String,
        val expiresAt: Long,
        val lastRefreshAttempt: Long = 0,
        val refreshAttempts: Int = 0
    )

    sealed class TokenRefreshEvent {
        data class RefreshStarted(val providerId: String) : TokenRefreshEvent()
        data class RefreshSuccess(val providerId: String, val newToken: String) : TokenRefreshEvent()
        data class RefreshFailed(val providerId: String, val error: String, val canRetry: Boolean) : TokenRefreshEvent()
        data class TokenExpired(val providerId: String) : TokenRefreshEvent()
    }

    private val callbacks = ConcurrentHashMap<String, suspend (String) -> Result<String>>()

    suspend fun initialize() {
        try {
            val tokenKeys = mutableListOf<String>()
            // FIX: Access underlying DataStore correctly via map
            // SecureDataStore.data returns DataStore<Preferences>
            val preferences = secureDataStore.data.data.first()
            val allKeys = preferences.asMap().keys
            allKeys.forEach { key ->
                if (key.name.startsWith(TOKEN_DATA_PREFIX)) {
                    tokenKeys.add(key.name)
                }
            }

            tokenKeys.forEach { key ->
                try {
                    val json = secureDataStore.getString(key)
                    if (json != null) {
                        val persistentData = gson.fromJson<PersistentTokenData>(
                            json,
                            object : TypeToken<PersistentTokenData>() {}.type
                        )

                        tokens[persistentData.providerId] = TokenInfo(
                            providerId = persistentData.providerId,
                            token = persistentData.token,
                            expiresAt = persistentData.expiresAt,
                            refreshCallback = { _ ->
                                Result.failure(Exception("Refresh callback not re-registered after restart"))
                            },
                            lastRefreshAttempt = persistentData.lastRefreshAttempt,
                            refreshAttempts = persistentData.refreshAttempts
                        )

                        if (tokens[persistentData.providerId]?.isExpired() == true) {
                            Log.w(TAG, "Restored token for ${persistentData.providerId} is expired")
                            _tokenRefreshEvents.value = TokenRefreshEvent.TokenExpired(persistentData.providerId)
                        } else {
                            tokens[persistentData.providerId]?.let { scheduleRefresh(persistentData.providerId, it) }
                        }

                        Log.d(TAG, "Successfully restored token for ${persistentData.providerId}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore token from key $key", e)
                }
            }

            Log.i(TAG, "TokenRefreshManager initialized with ${tokens.size} tokens")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TokenRefreshManager", e)
        }
    }

    fun registerToken(
        providerId: String,
        token: String,
        expiresInSeconds: Long,
        refreshCallback: suspend (String) -> Result<String>
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        val tokenInfo = TokenInfo(
            providerId = providerId,
            token = token,
            expiresAt = expiresAt,
            refreshCallback = refreshCallback
        )

        tokens[providerId] = tokenInfo
        callbacks[providerId] = refreshCallback

        scope.launch {
            persistTokenData(providerId, tokenInfo)
        }

        scheduleRefresh(providerId, tokenInfo)
        Log.d(TAG, "Registered token for $providerId, expires in ${expiresInSeconds}s")
    }

    suspend fun getValidToken(providerId: String): String? {
        val tokenInfo = tokens[providerId] ?: return null

        if (tokenInfo.isExpired()) {
            Log.w(TAG, "Token for $providerId is expired, attempting immediate refresh")
            _tokenRefreshEvents.value = TokenRefreshEvent.TokenExpired(providerId)
            return refreshTokenNow(providerId)
        }

        if (tokenInfo.isNearExpiry() && tokenInfo.canRefresh()) {
            Log.d(TAG, "Token for $providerId is near expiry, triggering background refresh")
            scope.launch {
                refreshTokenNow(providerId)
            }
        }

        return tokenInfo.token
    }

    suspend fun refreshTokenNow(providerId: String): String? = withContext(Dispatchers.IO) {
        val tokenInfo = tokens[providerId] ?: return@withContext null

        if (!tokenInfo.canRefresh()) {
            Log.d(TAG, "Skipping refresh for $providerId - too soon since last attempt")
            return@withContext tokenInfo.token
        }

        tokenInfo.lastRefreshAttempt = System.currentTimeMillis()
        _tokenRefreshEvents.value = TokenRefreshEvent.RefreshStarted(providerId)

        var attempt = 0
        var delayTime = INITIAL_RETRY_DELAY_MS

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                Log.d(TAG, "Refreshing token for $providerId (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)")

                val result = tokenInfo.refreshCallback(tokenInfo.token)

                if (result.isSuccess) {
                    val newToken = result.getOrNull() ?: return@withContext null

                    val newTokenInfo = tokenInfo.copy(
                        token = newToken,
                        expiresAt = System.currentTimeMillis() + (3600 * 1000),
                        refreshAttempts = 0
                    )
                    tokens[providerId] = newTokenInfo
                    persistTokenData(providerId, newTokenInfo)
                    scheduleRefresh(providerId, newTokenInfo)

                    Log.i(TAG, "Successfully refreshed token for $providerId")
                    _tokenRefreshEvents.value = TokenRefreshEvent.RefreshSuccess(providerId, newToken)

                    return@withContext newToken
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.w(TAG, "Token refresh attempt ${attempt + 1} failed for $providerId: $error")

                    if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                        delay(delayTime)
                        delayTime *= 2
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh exception for $providerId", e)
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(delayTime)
                    delayTime *= 2
                }
            }

            attempt++
        }

        tokenInfo.refreshAttempts = attempt
        _tokenRefreshEvents.value = TokenRefreshEvent.RefreshFailed(
            providerId = providerId,
            error = "Failed after $attempt attempts",
            canRetry = true
        )

        Log.e(TAG, "Failed to refresh token for $providerId after $attempt attempts")
        return@withContext null
    }

    private suspend fun persistTokenData(providerId: String, tokenInfo: TokenInfo) {
        try {
            val persistentData = PersistentTokenData(
                providerId = tokenInfo.providerId,
                token = tokenInfo.token,
                expiresAt = tokenInfo.expiresAt,
                lastRefreshAttempt = tokenInfo.lastRefreshAttempt,
                refreshAttempts = tokenInfo.refreshAttempts
            )
            val json = gson.toJson(persistentData)
            secureDataStore.putString(TOKEN_DATA_PREFIX + providerId, json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist token data for $providerId", e)
        }
    }

    fun unregisterToken(providerId: String) {
        tokens.remove(providerId)
        callbacks.remove(providerId)
        refreshJobs[providerId]?.cancel()
        refreshJobs.remove(providerId)

        scope.launch {
            try {
                secureDataStore.remove(TOKEN_DATA_PREFIX + providerId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove token data for $providerId", e)
            }
        }

        Log.d(TAG, "Unregistered token for $providerId")
    }

    fun hasToken(providerId: String): Boolean = tokens.containsKey(providerId)

    fun getTokenExpiration(providerId: String): Long? = tokens[providerId]?.expiresAt

    fun getTimeUntilExpiration(providerId: String): Duration? {
        val tokenInfo = tokens[providerId] ?: return null
        val remaining = tokenInfo.expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining.milliseconds else Duration.ZERO
    }

    private fun scheduleRefresh(providerId: String, tokenInfo: TokenInfo) {
        refreshJobs[providerId]?.cancel()

        val refreshAt = tokenInfo.expiresAt - REFRESH_BUFFER_MINUTES.minutes.inWholeMilliseconds
        val delayTime = (refreshAt - System.currentTimeMillis()).coerceAtLeast(0)

        Log.d(TAG, "Scheduling refresh for $providerId in ${delayTime / 1000}s")

        val job = scope.launch {
            try {
                delay(delayTime)

                if (tokens[providerId] == tokenInfo) {
                    Log.d(TAG, "Scheduled refresh triggered for $providerId")
                    refreshTokenNow(providerId)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Scheduled refresh failed for $providerId", e)
                    _tokenRefreshEvents.value = TokenRefreshEvent.RefreshFailed(
                        providerId = providerId,
                        error = "Scheduled refresh error: ${e.message}",
                        canRetry = true
                    )
                }
            }
        }

        refreshJobs[providerId] = job
    }

    fun getRegisteredProviders(): Set<String> = tokens.keys.toSet()

    fun getRefreshStats(): Map<String, RefreshStats> {
        return tokens.mapValues { (_, info) ->
            RefreshStats(
                providerId = info.providerId,
                isExpired = info.isExpired(),
                isNearExpiry = info.isNearExpiry(),
                expiresAt = info.expiresAt,
                refreshAttempts = info.refreshAttempts,
                lastRefreshAttempt = info.lastRefreshAttempt
            )
        }
    }

    data class RefreshStats(
        val providerId: String,
        val isExpired: Boolean,
        val isNearExpiry: Boolean,
        val expiresAt: Long,
        val refreshAttempts: Int,
        val lastRefreshAttempt: Long
    )

    fun shutdown() {
        scope.cancel()
        refreshJobs.values.forEach { it.cancel() }
        refreshJobs.clear()
        tokens.clear()
        callbacks.clear()
        Log.d(TAG, "TokenRefreshManager shut down")
    }
}
