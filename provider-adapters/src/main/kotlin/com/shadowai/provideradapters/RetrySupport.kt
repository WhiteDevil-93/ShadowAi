package com.shadowai.provideradapters

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.min

/**
 * Default HTTP status codes that are considered retryable.
 */
val DEFAULT_RETRYABLE_CODES = setOf(
    408, // Request Timeout (M-3: Added HTTP 408 for timeout scenarios)
    429, // Too Many Requests
    500, // Internal Server Error
    502, // Bad Gateway
    503, // Service Unavailable
    504  // Gateway Timeout
)

/**
 * Exception thrown when all retry attempts have been exhausted.
 */
class RetryExhaustedException(
    message: String,
    val lastException: Throwable,
    val attempts: Int
) : Exception(message, lastException)

/**
 * Configuration for retry behavior.
 *
 * @property maxRetries Maximum number of retry attempts (default: 3)
 * @property initialDelay Initial delay before first retry in milliseconds (default: 1000ms)
 * @property maxDelay Maximum delay between retries in milliseconds (default: 10000ms)
 * @property exponentialBackoff Whether to use exponential backoff (default: true)
 * @property retryableCodes HTTP status codes that trigger a retry (default: [429, 500, 502, 503, 504])
 * @property retryableExceptions Exception types that are retryable (default: IOException, SocketTimeoutException, HttpException)
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelay: Long = 1000L,
    val maxDelay: Long = 10000L,
    val exponentialBackoff: Boolean = true,
    val retryableCodes: Set<Int> = DEFAULT_RETRYABLE_CODES,
    val retryableExceptions: Set<Class<out Throwable>> = setOf(
        IOException::class.java,
        SocketTimeoutException::class.java,
        HttpException::class.java
    )
)

/**
 * Executes a suspendable block with configurable retry logic.
 *
 * This function implements exponential backoff with jitter and handles various
 * retryable failure scenarios including rate limiting (429), server errors (5xx),
 * and network timeouts.
 *
 * @param maxRetries Maximum number of retry attempts
 * @param initialDelay Initial delay before first retry in milliseconds
 * @param maxDelay Maximum delay between retries in milliseconds
 * @param retryableCodes HTTP status codes that should trigger a retry
 * @param block The suspendable operation to execute
 * @return The result of the successful block execution
 * @throws RetryExhaustedException if all retry attempts fail
 */
suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 10000L,
    retryableCodes: Set<Int> = DEFAULT_RETRYABLE_CODES,
    block: suspend () -> T
): T {
    val config = RetryConfig(
        maxRetries = maxRetries,
        initialDelay = initialDelay,
        maxDelay = maxDelay,
        retryableCodes = retryableCodes
    )
    return withRetry(config, block)
}

/**
 * Executes a suspendable block with configurable retry logic using a [RetryConfig].
 *
 * @param config The retry configuration
 * @param block The suspendable operation to execute
 * @return The result of the successful block execution
 * @throws RetryExhaustedException if all retry attempts fail
 */
suspend fun <T> withRetry(
    config: RetryConfig,
    block: suspend () -> T
): T {
    var lastException: Throwable? = null
    var currentDelay = config.initialDelay

    // The old coroutine retry loop has been removed, replaced with a standard loop
    repeat(config.maxRetries + 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e

            // Check if this exception is retryable
            if (attempt < config.maxRetries && isRetryableException(e, config)) {
                // FIX H-21: Increase jitter from 10% to 30% for better thundering herd prevention
                val jitter = (Math.random() * 0.3 * currentDelay).toLong()
                val delayWithJitter = currentDelay + jitter

                delay(delayWithJitter)

                // Calculate next delay with exponential backoff
                if (config.exponentialBackoff) {
                    currentDelay = min((currentDelay * 2), config.maxDelay)
                }
            } else {
                // Not retryable or out of retries
                throw RetryExhaustedException(
                    "Operation failed after ${attempt + 1} attempt(s)",
                    e,
                    attempt + 1
                )
            }
        }
    }

    // Should not reach here, but for type safety
    throw RetryExhaustedException(
        "Operation failed after ${config.maxRetries + 1} attempts",
        lastException ?: IllegalStateException("Unknown error"),
        config.maxRetries + 1
    )
}

/**
 * Checks if an exception is retriable based on the provided configuration.
 *
 * This function evaluates:
 * - HTTP status codes (for HttpException)
 * - Exception type matching against retryable types
 * - Specific error conditions like timeouts
 *
 * @param e The exception to evaluate
 * @return true if the exception indicates a retryable condition
 */
fun isRetryableException(e: Exception): Boolean {
    return isRetryableException(e, RetryConfig())
}

/**
 * Checks if an exception is retryable based on a specific configuration.
 *
 * @param e The exception to evaluate
 * @param config The retry configuration
 * @return true if the exception indicates a retryable condition
 */
fun isRetryableException(e: Exception, config: RetryConfig): Boolean {
    // Check for HTTP exceptions with retryable status codes
    if (e is HttpException) {
        return e.code() in config.retryableCodes
    }

    // Check for socket timeout specifically
    if (e is SocketTimeoutException) {
        return true
    }

    // Check for network IO exceptions
    if (e is IOException) {
        // Don't retry if it's a 4xx client error wrapped in IOException
        // (unless we can determine it's from a retryable code)
        val message = e.message?.lowercase() ?: ""

        // Don't retry authentication errors
        if (message.contains("unauthorized") ||
            message.contains("forbidden") ||
            message.contains("401") ||
            message.contains("403")) {
            return false
        }

        return true
    }

    // Check against registered retryable exception types
    for (retryableType in config.retryableExceptions) {
        if (retryableType.isInstance(e)) {
            return true
        }
    }

    return false
}

/**
 * Extension function for ProviderAdapter to execute an operation with default retry.
 * Uses the adapter's config.maxRetries setting.
 *
 * @param block The suspendable operation to execute
 * @return The result of the successful operation
 */
suspend fun <T> ProviderAdapter.withRetry(block: suspend () -> T): T {
    return com.shadowai.provideradapters.withRetry(
        maxRetries = config.maxRetries,
        block = block
    )
}

/**
 * Extension function to create a custom retry configuration based on adapter settings.
 */
fun ProviderAdapter.createRetryConfig(): RetryConfig {
    return RetryConfig(
        maxRetries = config.maxRetries,
        initialDelay = 1000L,
        maxDelay = 10000L,
        exponentialBackoff = true
    )
}
