package com.shadowai.app.auth

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * Features:
 * - Automatic token refresh before expiration
 * - Background refresh scheduling
 * - Token expiration tracking
 * - Retry logic with exponential backoff
 * - Multiple provider support
 *
 * Usage:
 * ```
 * // Register a token with refresh callback
 * tokenRefreshManager.registerToken(
 *     providerId = "openai",
 *     token = "eyJ...",
 *     expiresInSeconds = 3600,
 *     refreshCallback = { oldToken ->
 *         // Call your auth API to get new token
 *         authApi.refreshToken(oldToken)
 *     }
 * )
 *
 * // Get current valid token
 * val token = tokenRefreshManager.getValidToken("openai")
 * ```
 */
@Singleton
class TokenRefreshManager @Inject constructor() {

    private companion object {
        private const val TAG = "TokenRefresh"
        private const val REFRESH_BUFFER_MINUTES = 5L // Refresh 5 minutes before expiry
        private const val MIN_REFRESH_INTERVAL_SECONDS = 60L // Don't refresh more than once per minute
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val refreshJobs = ConcurrentHashMap<String, Job>()

    private val _tokenRefreshEvents = MutableStateFlow<TokenRefreshEvent?>(null)
    val tokenRefreshEvents: StateFlow<TokenRefreshEvent?> = _tokenRefreshEvents.asStateFlow()

    /**
     * Token information with expiration tracking
     */
    data class TokenInfo(
        val providerId: String,
        val token: String,
        val expiresAt: Long, // Unix timestamp in milliseconds
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

    /**
     * Token refresh events
     */
    sealed class TokenRefreshEvent {
        data class RefreshStarted(val providerId: String) : TokenRefreshEvent()
        data class RefreshSuccess(val providerId: String, val newToken: String) : TokenRefreshEvent()
        data class RefreshFailed(val providerId: String, val error: String, val canRetry: Boolean) : TokenRefreshEvent()
        data class TokenExpired(val providerId: String) : TokenRefreshEvent()
    }

    /**
     * Register a token with automatic refresh
     *
     * @param providerId Unique identifier for the provider
     * @param token Current JWT token
     * @param expiresInSeconds Token expiration in seconds from now
     * @param refreshCallback Callback to refresh the token
     */
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
        scheduleRefresh(providerId, tokenInfo)

        Log.d(TAG, "Registered token for $providerId, expires in ${expiresInSeconds}s")
    }

    /**
     * Get a valid token for the provider, refreshing if necessary
     *
     * @return Current valid token, or null if expired and refresh failed
     */
    suspend fun getValidToken(providerId: String): String? {
        val tokenInfo = tokens[providerId] ?: return null

        // If token is expired, try immediate refresh
        if (tokenInfo.isExpired()) {
            Log.w(TAG, "Token for $providerId is expired, attempting immediate refresh")
            _tokenRefreshEvents.value = TokenRefreshEvent.TokenExpired(providerId)
            return refreshTokenNow(providerId)
        }

        // If token is near expiry and can be refreshed, refresh in background
        if (tokenInfo.isNearExpiry() && tokenInfo.canRefresh()) {
            Log.d(TAG, "Token for $providerId is near expiry, triggering background refresh")
            scope.launch {
                refreshTokenNow(providerId)
            }
        }

        return tokenInfo.token
    }

    /**
     * Manually trigger token refresh
     *
     * @return New token or null if refresh failed
     */
    suspend fun refreshTokenNow(providerId: String): String? = withContext(Dispatchers.IO) {
        val tokenInfo = tokens[providerId] ?: return@withContext null

        if (!tokenInfo.canRefresh()) {
            Log.d(TAG, "Skipping refresh for $providerId - too soon since last attempt")
            return@withContext tokenInfo.token
        }

        tokenInfo.lastRefreshAttempt = System.currentTimeMillis()
        _tokenRefreshEvents.value = TokenRefreshEvent.RefreshStarted(providerId)

        var attempt = 0
        var delay = INITIAL_RETRY_DELAY_MS

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                Log.d(TAG, "Refreshing token for $providerId (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)")

                val result = tokenInfo.refreshCallback(tokenInfo.token)

                if (result.isSuccess) {
                    val newToken = result.getOrNull() ?: return@withContext null

                    // Update token info
                    val newTokenInfo = tokenInfo.copy(
                        token = newToken,
                        // Assume 1 hour expiry if not specified
                        expiresAt = System.currentTimeMillis() + (3600 * 1000),
                        refreshAttempts = 0
                    )
                    tokens[providerId] = newTokenInfo

                    // Reschedule refresh
                    scheduleRefresh(providerId, newTokenInfo)

                    Log.i(TAG, "Successfully refreshed token for $providerId")
                    _tokenRefreshEvents.value = TokenRefreshEvent.RefreshSuccess(providerId, newToken)

                    return@withContext newToken
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.w(TAG, "Token refresh attempt ${attempt + 1} failed for $providerId: $error")

                    if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                        delay(delay)
                        delay *= 2 // Exponential backoff
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh exception for $providerId", e)
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(delay)
                    delay *= 2
                }
            }

            attempt++
        }

        // All attempts failed
        tokenInfo.refreshAttempts = attempt
        _tokenRefreshEvents.value = TokenRefreshEvent.RefreshFailed(
            providerId = providerId,
            error = "Failed after $attempt attempts",
            canRetry = true
        )

        Log.e(TAG, "Failed to refresh token for $providerId after $attempt attempts")
        return@withContext null
    }

    /**
     * Unregister a token and cancel refresh scheduling
     */
    fun unregisterToken(providerId: String) {
        tokens.remove(providerId)
        refreshJobs[providerId]?.cancel()
        refreshJobs.remove(providerId)
        Log.d(TAG, "Unregistered token for $providerId")
    }

    /**
     * Check if a token is registered
     */
    fun hasToken(providerId: String): Boolean = tokens.containsKey(providerId)

    /**
     * Get token expiration time
     *
     * @return Unix timestamp in milliseconds, or null if not registered
     */
    fun getTokenExpiration(providerId: String): Long? = tokens[providerId]?.expiresAt

    /**
     * Get time until expiration
     *
     * @return Duration until expiration, or null if not registered
     */
    fun getTimeUntilExpiration(providerId: String): Duration? {
        val tokenInfo = tokens[providerId] ?: return null
        val remaining = tokenInfo.expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining.toDuration(DurationUnit.MILLISECONDS) else Duration.ZERO
    }

    /**
     * Schedule automatic token refresh
     */
    private fun scheduleRefresh(providerId: String, tokenInfo: TokenInfo) {
        // Cancel existing job
        refreshJobs[providerId]?.cancel()

        // Calculate delay until refresh (refresh 5 minutes before expiry)
        val refreshAt = tokenInfo.expiresAt - REFRESH_BUFFER_MINUTES.minutes.inWholeMilliseconds
        val delay = (refreshAt - System.currentTimeMillis()).coerceAtLeast(0)

        Log.d(TAG, "Scheduling refresh for $providerId in ${delay / 1000}s")

        val job = scope.launch {
            try {
                delay(delay)

                if (tokens[providerId] == tokenInfo) { // Ensure token hasn't been updated
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

    /**
     * Get all registered provider IDs
     */
    fun getRegisteredProviders(): Set<String> = tokens.keys.toSet()

    /**
     * Get refresh statistics for debugging
     */
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

    /**
     * Cleanup resources
     */
    fun shutdown() {
        scope.cancel()
        refreshJobs.values.forEach { it.cancel() }
        refreshJobs.clear()
        tokens.clear()
        Log.d(TAG, "TokenRefreshManager shut down")
    }
}
