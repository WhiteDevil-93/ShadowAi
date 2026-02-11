package com.shadowai.app.execution

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coroutine-safe token bucket rate limiter with per-endpoint configuration.
 */
@Singleton
class RateLimiter @Inject constructor() {

    companion object {
        private const val TAG = "RateLimiter"
        private const val DEFAULT_ENDPOINT = "__default__"

        private const val DEFAULT_REQUESTS_PER_MINUTE = 60
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 60_000L
    }

    data class EndpointConfig(
        val requestsPerMinute: Int = DEFAULT_REQUESTS_PER_MINUTE,
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val baseDelayMs: Long = DEFAULT_BASE_DELAY_MS
    )

    private data class TokenBucket(
        var tokens: Double,
        var lastRefillMs: Long
    )

    private var defaultConfig = EndpointConfig()
    private val endpointConfigs = mutableMapOf<String, EndpointConfig>()
    private val endpointBuckets = mutableMapOf<String, TokenBucket>()

    private var consecutiveFailures = 0
    private val mutex = Mutex()

    fun configure(
        requestsPerMinute: Int = DEFAULT_REQUESTS_PER_MINUTE,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS
    ) {
        defaultConfig = EndpointConfig(
            requestsPerMinute = requestsPerMinute,
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs
        )
    }

    fun configureEndpoint(
        endpoint: String,
        requestsPerMinute: Int = DEFAULT_REQUESTS_PER_MINUTE,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS
    ) {
        endpointConfigs[endpoint] = EndpointConfig(
            requestsPerMinute = requestsPerMinute,
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs
        )
    }

    suspend fun <T> execute(block: suspend () -> T): T = execute(DEFAULT_ENDPOINT, block)

    suspend fun <T> execute(endpoint: String, block: suspend () -> T): T {
        var lastException: Exception? = null
        var retryCount = 0
        val config = endpointConfigs[endpoint] ?: defaultConfig

        while (retryCount <= config.maxRetries) {
            awaitRateLimit(endpoint, config)

            try {
                val result = block()
                mutex.withLock { consecutiveFailures = 0 }
                return result
            } catch (e: Exception) {
                lastException = e
                if (!isRateLimitError(e)) {
                    throw e
                }

                retryCount++
                val delayMs = calculateBackoff(retryCount, config.baseDelayMs)
                Log.w(TAG, "Rate limit hit on endpoint=$endpoint, retry $retryCount/${config.maxRetries} after ${delayMs}ms")
                delay(delayMs)
            }
        }

        mutex.withLock { consecutiveFailures += 1 }
        throw RateLimitExceededException(
            "Rate limit exceeded after ${config.maxRetries} retries on endpoint=$endpoint",
            lastException
        )
    }

    suspend fun <T> executeOnce(block: suspend () -> T): T = executeOnce(DEFAULT_ENDPOINT, block)

    suspend fun <T> executeOnce(endpoint: String, block: suspend () -> T): T {
        val config = endpointConfigs[endpoint] ?: defaultConfig
        awaitRateLimit(endpoint, config)
        return block()
    }

    private suspend fun awaitRateLimit(endpoint: String, config: EndpointConfig) {
        while (true) {
            val waitMs = mutex.withLock {
                val now = System.currentTimeMillis()
                val bucket = endpointBuckets.getOrPut(endpoint) {
                    TokenBucket(
                        tokens = config.requestsPerMinute.toDouble(),
                        lastRefillMs = now
                    )
                }
                refillBucket(bucket, config.requestsPerMinute, now)
                if (bucket.tokens >= 1.0) {
                    bucket.tokens -= 1.0
                    0L
                } else {
                    val refillPerMs = config.requestsPerMinute.toDouble() / 60_000.0
                    max(1L, ((1.0 - bucket.tokens) / refillPerMs).toLong())
                }
            }
            if (waitMs <= 0L) return
            Log.d(TAG, "Rate limit reached for endpoint=$endpoint, waiting ${waitMs}ms")
            delay(waitMs)
        }
    }

    private fun refillBucket(bucket: TokenBucket, rpm: Int, nowMs: Long) {
        if (rpm <= 0) {
            bucket.tokens = 0.0
            bucket.lastRefillMs = nowMs
            return
        }

        val elapsedMs = max(0L, nowMs - bucket.lastRefillMs)
        val refillPerMs = rpm.toDouble() / 60_000.0
        val replenished = elapsedMs * refillPerMs
        bucket.tokens = min(rpm.toDouble(), bucket.tokens + replenished)
        bucket.lastRefillMs = nowMs
    }

    private fun calculateBackoff(retryCount: Int, baseDelayMs: Long): Long {
        val safeBase = baseDelayMs.coerceAtLeast(1L)
        val exponentialDelay = safeBase * (1L shl (retryCount - 1))
        val jitter = Random.nextLong(0L, (exponentialDelay * 3 / 10).coerceAtLeast(1L))
        return min(exponentialDelay + jitter, MAX_DELAY_MS)
    }

    private fun isRateLimitError(e: Exception): Boolean {
        return e is RateLimitSignalException || (e is HttpStatusException && e.statusCode == 429)
    }

    suspend fun getCurrentRequestCount(endpoint: String = DEFAULT_ENDPOINT): Int {
        return mutex.withLock {
            val config = endpointConfigs[endpoint] ?: defaultConfig
            val bucket = endpointBuckets[endpoint] ?: return@withLock 0
            val now = System.currentTimeMillis()
            refillBucket(bucket, config.requestsPerMinute, now)
            (config.requestsPerMinute - bucket.tokens).toInt().coerceAtLeast(0)
        }
    }

    suspend fun getConsecutiveFailures(): Int = mutex.withLock { consecutiveFailures }

    suspend fun reset() {
        mutex.withLock {
            endpointBuckets.clear()
            consecutiveFailures = 0
        }
    }
}

class RateLimitExceededException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class RateLimitSignalException(message: String) : Exception(message)

class HttpStatusException(
    val statusCode: Int,
    message: String
) : Exception(message)
