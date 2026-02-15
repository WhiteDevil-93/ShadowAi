package com.shadowai.app.util

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Retry policy configurations and utilities for resilient operations.
 * 
 * Provides configurable retry strategies with exponential backoff,
 * jitter, and customizable retry conditions.
 * 
 * Usage:
 * ```kotlin
 * // With default policy
 * val result = RetryPolicy.DEFAULT.execute {
 *     apiCall()
 * }
 * 
 * // With custom policy
 * val policy = RetryPolicy(maxRetries = 5, baseDelayMs = 500)
 * val result = policy.execute { 
 *     riskyOperation() 
 * }
 * ```
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.2,
    val retryOn: (Throwable) -> Boolean = { true }
) {
    companion object {
        private const val TAG = "RetryPolicy"
        
        /**
         * Default retry policy: 3 retries with exponential backoff.
         */
        val DEFAULT = RetryPolicy()
        
        /**
         * Aggressive retry policy: 5 retries with shorter delays.
         */
        val AGGRESSIVE = RetryPolicy(
            maxRetries = 5,
            baseDelayMs = 500,
            maxDelayMs = 10000
        )
        
        /**
         * Conservative retry policy: 2 retries with longer delays.
         */
        val CONSERVATIVE = RetryPolicy(
            maxRetries = 2,
            baseDelayMs = 2000,
            maxDelayMs = 60000
        )
        
        /**
         * Network-specific retry: Only retry on network errors.
         * M-3: Including HTTP 408 (Request Timeout) in retry conditions.
         */
        val NETWORK_ONLY = RetryPolicy(
            maxRetries = 3,
            baseDelayMs = 1000,
            retryOn = { e ->
                val message = e.message?.lowercase() ?: ""
                val className = e.javaClass.simpleName.lowercase()
                
                val isNetworkError = className.contains("socket") ||
                    className.contains("connect") ||
                    className.contains("timeout") ||
                    message.contains("network") ||
                    message.contains("connection") ||
                    message.contains("timeout") ||
                    message.contains("unreachable")
                
                // M-3: Check for HTTP 408 (Request Timeout) in message
                val isHttp408 = message.contains("408") ||
                    message.contains("request timeout")
                
                // M-3: Check for other retryable HTTP status codes
                val isRetryableHttpCode = message.contains("429") ||  // Rate limited
                    message.contains("503") ||  // Service unavailable
                    message.contains("502") ||  // Bad gateway
                    message.contains("504")    // Gateway timeout
                
                isNetworkError || isHttp408 || isRetryableHttpCode
            }
        )
        
        /**
         * M-3: HTTP-specific retry policy - specifically handles HTTP 408 and other retryable codes.
         */
        val HTTP_RETRYABLE = RetryPolicy(
            maxRetries = 3,
            baseDelayMs = 2000,
            maxDelayMs = 30000,
            retryOn = { e ->
                val message = e.message?.lowercase() ?: ""
                
                // HTTP status codes that are safe to retry
                val retryableCodes = listOf(408, 429, 500, 502, 503, 504)
                
                retryableCodes.any { code ->
                    message.contains(code.toString()) ||
                    message.contains("http $code")
                } || message.contains("timeout") || message.contains("rate limit")
            }
        )
        
        /**
         * No retry policy - executes once.
         */
        val NO_RETRY = RetryPolicy(maxRetries = 0)
    }

    /**
     * Execute a block with retry according to this policy.
     * 
     * @param block The suspend block to execute
     * @return Result of successful execution
     * @throws Throwable if all retries are exhausted
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var lastException: Throwable? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                
                if (attempt >= maxRetries || !retryOn(e)) {
                    throw e
                }
                
                val delayMs = calculateDelay(attempt)
                Log.d(TAG, "Retry ${attempt + 1}/$maxRetries after ${delayMs}ms: ${e.message}")
                delay(delayMs)
            }
        }
        
        throw lastException ?: RuntimeException("Retry failed with no exception")
    }

    /**
     * Execute a block with retry, returning Result instead of throwing.
     */
    suspend fun <T> executeAsResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(execute(block))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Calculate delay for a given attempt using exponential backoff with jitter.
     */
    fun calculateDelay(attempt: Int): Long {
        val exponentialDelay = (baseDelayMs * multiplier.pow(attempt.toDouble())).toLong()
        val cappedDelay = min(exponentialDelay, maxDelayMs)
        
        // Add jitter to prevent thundering herd
        val jitter = (cappedDelay * jitterFactor * (Random.nextDouble() * 2 - 1)).toLong()
        
        return maxOf(0, cappedDelay + jitter)
    }

    /**
     * Create a modified policy with different max retries.
     */
    fun withMaxRetries(retries: Int): RetryPolicy = copy(maxRetries = retries)

    /**
     * Create a modified policy with a custom retry condition.
     */
    fun withRetryCondition(condition: (Throwable) -> Boolean): RetryPolicy = 
        copy(retryOn = condition)

    /**
     * Create a modified policy with different base delay.
     */
    fun withBaseDelay(delayMs: Long): RetryPolicy = copy(baseDelayMs = delayMs)
}

/**
 * Extension function for easy retry on suspend functions.
 */
suspend fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy.DEFAULT,
    block: suspend () -> T
): T = policy.execute(block)

/**
 * Extension function for optional retry based on Result.
 */
suspend fun <T> Result<T>.retryOnFailure(
    policy: RetryPolicy = RetryPolicy.DEFAULT,
    block: suspend () -> T
): Result<T> {
    return if (isFailure) {
        policy.executeAsResult(block)
    } else {
        this
    }
}
