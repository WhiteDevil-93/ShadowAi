package com.shadowai.app.security

/**
 * Utility for redacting API keys from strings (logs, error messages, etc.)
 *
 * M-17: Prevents accidental exposure of sensitive API keys in error logs.
 */
object ApiKeyRedactor {

    private const val REDACTED_PLACEHOLDER = "[API_KEY_REDACTED]"

    // Common API key patterns and header names
    private val SENSITIVE_PATTERNS = listOf(
        // URL query parameter patterns
        Regex("([&?])key=[^&]*", RegexOption.IGNORE_CASE),
        Regex("([&?])api[_-]?key=[^&]*", RegexOption.IGNORE_CASE),
        Regex("([&?])apikey=[^&]*", RegexOption.IGNORE_CASE),
        Regex("([&?])token=[^&]*", RegexOption.IGNORE_CASE),
        Regex("([&?])auth=[^&]*", RegexOption.IGNORE_CASE),

        // JSON patterns (common in error responses)
        Regex(""""key"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE),
        Regex(""""api[_-]?key"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE),
        Regex(""""apikey"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE),
        Regex(""""token"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE),
        Regex(""""x-goog-api-key"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE),

        // Header patterns - FIX: Escaped backslashes for Kotlin strings
        Regex("x-goog-api-key:\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("Authorization:\\s*Bearer\\s+\\S+", RegexOption.IGNORE_CASE),
        Regex("Authorization:\\s*ApiKey\\s+\\S+", RegexOption.IGNORE_CASE),

        // Generic 32+ character alphanumeric strings (likely API keys)
        // Only when preceded by key-related terms
        // FIX: Escaped backslashes for Kotlin strings
        Regex("(?i)(key[=:]\\s*)([a-z0-9_-]{32,})")
    )

    /**
     * Redacts sensitive information from a string.
     *
     * @param text The text to redact
     * @return The redacted text with API keys replaced
     */
    fun redact(text: String): String {
        if (text.isBlank()) return text

        var redacted = text

        SENSITIVE_PATTERNS.forEach { pattern ->
            redacted = pattern.replace(redacted) { matchResult ->
                // For capturing groups, only replace the sensitive part
                if (matchResult.groupValues.size > 2) {
                    val prefix = matchResult.groupValues[1]
                    "$prefix$REDACTED_PLACEHOLDER"
                } else {
                    // Full match replacement
                    when {
                        matchResult.value.contains("=") -> {
                            val key = matchResult.value.substringBefore("=")
                            "$key=$REDACTED_PLACEHOLDER"
                        }
                        matchResult.value.contains(":") -> {
                            val key = matchResult.value.substringBefore(":")
                            "$key: $REDACTED_PLACEHOLDER"
                        }
                        else -> REDACTED_PLACEHOLDER
                    }
                }
            }
        }

        return redacted
    }

    /**
     * Redacts sensitive information from an exception's message and causes.
     *
     * @param throwable The throwable to redact
     * @return A new throwable with redacted messages (if possible) or the original
     */
    fun redactThrowable(throwable: Throwable): Throwable {
        // For most exceptions, we can only redact the message
        // Creating a new exception preserves the stack trace
        val redactedMessage = redact(throwable.message ?: "")

        // Recursively redact the cause
        val redactedCause = throwable.cause?.let { redactThrowable(it) }

        return when (throwable) {
            is SecurityException -> SecurityException(redactedMessage, redactedCause)
            is IllegalArgumentException -> IllegalArgumentException(redactedMessage, redactedCause)
            is IllegalStateException -> IllegalStateException(redactedMessage, redactedCause)
            is java.io.IOException -> java.io.IOException(redactedMessage, redactedCause)
            else -> RuntimeException(redactedMessage, redactedCause)
        }.apply {
            stackTrace = throwable.stackTrace
        }
    }

    /**
     * Redacts sensitive information from a URL query string.
     *
     * @param url The URL to redact
     * @return The URL with API keys replaced
     */
    fun redactUrl(url: String): String {
        return redact(url)
    }
}
