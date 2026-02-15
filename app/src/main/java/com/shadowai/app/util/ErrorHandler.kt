package com.shadowai.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.shadowai.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handling and crash reporting utility.
 *
 * Provides consistent error logging, crash file generation for debug builds,
 * and categorization of exceptions for better debugging.
 *
 * Usage:
 * ```kotlin
 * try {
 *     riskyOperation()
 * } catch (e: Exception) {
 *     ErrorHandler.handle(e, "RiskyOperation", "Failed during processing")
 * }
 * ```
 */
@Singleton
class ErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ErrorHandler"
        private const val MAX_CRASH_FILES = 10

        /**
         * Error categories for better debugging and monitoring.
         */
        enum class Category {
            NETWORK,        // Connection, timeout, API errors
            SECURITY,       // Encryption, authentication failures
            DATABASE,       // Room, SQLite errors
            AI_INFERENCE,   // Model loading, inference errors
            UI,             // Compose, lifecycle errors
            DEVICE_ACTION,  // Telephony, SMS, media errors
            PARSING,        // JSON, response parsing errors
            UNKNOWN         // Uncategorized
        }

        /**
         * Categorize an exception based on its type and message.
         */
        fun categorize(e: Throwable): Category {
            val message = e.message?.lowercase() ?: ""
            val className = e.javaClass.simpleName.lowercase()

            return when {
                // Network errors
                className.contains("socket") ||
                className.contains("connect") ||
                className.contains("timeout") ||
                className.contains("http") ||
                message.contains("network") ||
                message.contains("connection") ||
                message.contains("timeout") -> Category.NETWORK

                // Security errors
                className.contains("security") ||
                className.contains("crypto") ||
                className.contains("key") ||
                className.contains("auth") ||
                message.contains("encryption") ||
                message.contains("decrypt") ||
                message.contains("authentication") ||
                message.contains("permission denied") -> Category.SECURITY

                // Database errors
                className.contains("sql") ||
                className.contains("room") ||
                className.contains("database") ||
                message.contains("database") ||
                message.contains("sqlite") ||
                message.contains("migration") -> Category.DATABASE

                // AI inference errors
                className.contains("llama") ||
                className.contains("model") ||
                className.contains("inference") ||
                message.contains("model") ||
                message.contains("inference") ||
                message.contains("oom") ||
                message.contains("out of memory") -> Category.AI_INFERENCE

                // UI errors
                className.contains("compose") ||
                className.contains("lifecycle") ||
                className.contains("view") ||
                message.contains("compose") ||
                message.contains("recomposition") -> Category.UI

                // Device action errors
                message.contains("telephony") ||
                message.contains("sms") ||
                message.contains("call") ||
                message.contains("media") ||
                message.contains("accessibility") -> Category.DEVICE_ACTION

                // Parsing errors
                className.contains("json") ||
                className.contains("parse") ||
                message.contains("parse") ||
                message.contains("json") ||
                message.contains("malformed") -> Category.PARSING

                else -> Category.UNKNOWN
            }
        }

        /**
         * Get stack trace as string.
         */
        fun getStackTraceString(e: Throwable): String {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }

    /**
     * Handle an exception with logging and optional crash file generation.
     *
     * @param e The exception to handle
     * @param tag Log tag for identifying source
     * @param message Additional context message
     * @param fatal Whether this is a fatal error (generates crash file in debug)
     */
    fun handle(
        e: Throwable,
        tag: String = TAG,
        message: String = "An error occurred",
        fatal: Boolean = false
    ) {
        val category = categorize(e)
        val fullMessage = "[$category] $message: ${e.message}"

        // Log the error
        if (fatal) {
            Log.e(tag, fullMessage, e)
        } else {
            Log.w(tag, fullMessage, e)
        }

        // In debug builds, write crash files for fatal errors
        if (BuildConfig.DEBUG && fatal) {
            writeCrashFile(e, tag, message, category)
        }

        // NOTE: Crashlytics integration is active via Firebase plugin (firebase-crashlytics).
        // Fatal crashes are captured automatically. Non-fatal errors can be recorded with:
        // FirebaseCrashlytics.getInstance().recordException(e)
        // Uncomment below line to enable non-fatal error tracking:
        // com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
    }

    /**
     * Write a crash file for debugging.
     */
    private fun writeCrashFile(
        e: Throwable,
        tag: String,
        message: String,
        category: Category
    ) {
        try {
            val crashDir = File(context.filesDir, "crashes")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            // Clean up old crash files
            cleanupOldCrashFiles(crashDir)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val crashFile = File(crashDir, "crash_${timestamp}.txt")

            val deviceInfo = buildString {
                appendLine("=== CRASH REPORT ===")
                appendLine("Timestamp: ${Date()}")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine()
                appendLine("=== ERROR INFO ===")
                appendLine("Category: $category")
                appendLine("Tag: $tag")
                appendLine("Message: $message")
                appendLine("Exception: ${e.javaClass.name}")
                appendLine("Exception Message: ${e.message}")
                appendLine()
                appendLine("=== STACK TRACE ===")
                appendLine(getStackTraceString(e))
            }

            crashFile.writeText(deviceInfo)
            Log.i(TAG, "Crash file written: ${crashFile.absolutePath}")

        } catch (writeError: Exception) {
            Log.e(TAG, "Failed to write crash file", writeError)
        }
    }

    /**
     * Clean up old crash files, keeping only the most recent ones.
     */
    private fun cleanupOldCrashFiles(crashDir: File) {
        val crashFiles = crashDir.listFiles { file ->
            file.name.startsWith("crash_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (crashFiles.size > MAX_CRASH_FILES) {
            crashFiles.drop(MAX_CRASH_FILES).forEach { file ->
                file.delete()
            }
        }
    }

    /**
     * Log a warning without stack trace.
     */
    fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    /**
     * Log an info message.
     */
    fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    /**
     * Handle a recoverable error (logs as warning).
     */
    fun handleRecoverable(
        e: Throwable,
        tag: String = TAG,
        message: String = "Recoverable error"
    ) {
        handle(e, tag, message, fatal = false)
    }

    /**
     * Handle a fatal error (logs as error, writes crash file in debug).
     */
    fun handleFatal(
        e: Throwable,
        tag: String = TAG,
        message: String = "Fatal error"
    ) {
        handle(e, tag, message, fatal = true)
    }
}

/**
 * Extension function for easy error handling on any exception.
 */
fun Throwable.logAndContinue(tag: String, message: String = "Error occurred") {
    Log.w(tag, "[$tag] $message: ${this.message}", this)
}

/**
 * Extension function for Result type to log failures.
 */
fun <T> Result<T>.logFailure(tag: String, message: String = "Operation failed"): Result<T> {
    onFailure { e ->
        Log.w(tag, "$message: ${e.message}", e)
    }
    return this
}
