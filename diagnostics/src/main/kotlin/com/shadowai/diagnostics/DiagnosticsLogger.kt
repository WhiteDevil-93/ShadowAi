package com.shadowai.diagnostics

import android.util.Log

/**
 * Logs diagnostics events.
 */
interface DiagnosticsLogger {
    fun log(error: PipelineError)
    fun logEvent(name: String, data: Map<String, Any> = emptyMap())
}

/**
 * Logcat-backed diagnostics logger.
 */
class LogcatDiagnosticsLogger(
    private val tag: String = "Diagnostics"
) : DiagnosticsLogger {
    override fun log(error: PipelineError) {
        val message = error.toLogMessage()
        when (error.severity) {
            ErrorSeverity.INFO -> Log.i(tag, message, error.cause)
            ErrorSeverity.WARNING -> Log.w(tag, message, error.cause)
            ErrorSeverity.ERROR -> Log.e(tag, message, error.cause)
            ErrorSeverity.CRITICAL -> Log.wtf(tag, message, error.cause)
        }
    }

    override fun logEvent(name: String, data: Map<String, Any>) {
        val payload = if (data.isEmpty()) "" else " data=$data"
        Log.d(tag, "Event=$name$payload")
    }
}
