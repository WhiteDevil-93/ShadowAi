package com.shadowai.app.util

import android.util.Log
import com.shadowai.app.security.ApiKeyRedactor
import com.shadowai.app.exceptions.ExceptionMapper

/**
 * M-11: Standardized logging utilities.
 *
 * Provides consistent logging across the application with:
 * - Standardized log tags
 * - Automatic redaction of sensitive data
 * - Performance tracking
 * - Error context preservation
 */
object ShadowLogger {

    // Standard log tags
    const val TAG_APP = "ShadowAI"
    const val TAG_NETWORK = "Network"
    const val TAG_MODEL = "Model"
    const val TAG_EXECUTION = "Execution"
    const val TAG_SECURITY = "Security"
    const val TAG_DB = "Database"
    const val TAG_UI = "UI"
    const val TAG_PROVIDER = "Provider"
    const val TAG_CACHE = "Cache"

    /**
     * Log levels
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    /**
     * Log with automatic API key redaction.
     *
     * @param tag Log tag
     * @param level Log level
     * @param message Message to log (will redact API keys)
     * @param throwable Optional exception
     */
    private fun log(tag: String, level: Level, message: String, throwable: Throwable? = null) {
        val redactedMessage = ApiKeyRedactor.redact(message)
        when (level) {
            Level.VERBOSE -> Log.v(tag, redactedMessage, throwable)
            Level.DEBUG -> Log.d(tag, redactedMessage, throwable)
            Level.INFO -> Log.i(tag, redactedMessage, throwable)
            Level.WARN -> Log.w(tag, redactedMessage, throwable)
            Level.ERROR -> Log.e(tag, redactedMessage, throwable)
        }
    }

    /**
     * Verbose log with auto-redaction.
     */
    fun v(tag: String, message: String) {
        log(tag, Level.VERBOSE, message)
    }

    /**
     * Verbose log with exception.
     */
    fun v(tag: String, message: String, throwable: Throwable) {
        log(tag, Level.VERBOSE, message, throwable)
    }

    /**
     * Debug log with auto-redaction.
     */
    fun d(tag: String, message: String) {
        log(tag, Level.DEBUG, message)
    }

    /**
     * Debug log with exception.
     */
    fun d(tag: String, message: String, throwable: Throwable) {
        log(tag, Level.DEBUG, message, throwable)
    }

    /**
     * Info log with auto-redaction.
     */
    fun i(tag: String, message: String) {
        log(tag, Level.INFO, message)
    }

    /**
     * Info log with exception.
     */
    fun i(tag: String, message: String, throwable: Throwable) {
        log(tag, Level.INFO, message, throwable)
    }

    /**
     * Warning log with auto-redaction.
     */
    fun w(tag: String, message: String) {
        log(tag, Level.WARN, message)
    }

    /**
     * Warning log with exception.
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        log(tag, Level.WARN, message, throwable)
    }

    /**
     * Error log with auto-redaction and exception context.
     * M-11: Enhanced with ExceptionMapper context.
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        log(tag, Level.ERROR, message, throwable)
    }

    /**
     * Performance tracking - log operation duration.
     *
     * @param tag Log tag
     * @param operation Operation name
     * @param block Operation to measure
     * @return Operation result
     */
    inline fun <T> trackPerformance(tag: String, operation: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val durationMs = (System.nanoTime() - start) / 1_000_000
        Log.d(tag, "$operation completed in ${durationMs}ms")
        return result
    }

    /**
     * Performance tracking - suspend version.
     */
    suspend inline fun <T> trackPerformanceSuspend(
        tag: String,
        operation: String,
        block: suspend () -> T
    ): T {
        val start = System.nanoTime()
        val result = block()
        val durationMs = (System.nanoTime() - start) / 1_000_000
        Log.d(tag, "$operation completed in ${durationMs}ms")
        return result
    }

    /**
     * Network request logging with sanitized URLs.
     *
     * @param url Request URL (will be redacted)
     * @param method HTTP method
     * @param responseCode Response code
     */
    fun logNetworkRequest(url: String, method: String, responseCode: Int) {
        val redactedUrl = ApiKeyRedactor.redactUrl(url)
        Log.d(TAG_NETWORK, "$method $redactedUrl -> $responseCode")
    }

    /**
     * Error logging with context.
     * M-11: Integrates with ExceptionMapper for rich context.
     */
    fun logErrorWithContext(
        tag: String,
        message: String,
        throwable: Throwable,
        context: ExceptionMapper.ExceptionContext? = null
    ) {
        val contextInfo = context?.toSummary()
        val fullMessage = if (contextInfo != null) {
            "$message | Context: $contextInfo"
        } else {
            message
        }
        log(tag, Level.ERROR, fullMessage, throwable)
    }
}