package com.shadowai.app.security

import android.util.Log
import com.shadowai.app.BuildConfig

/**
 * M-17: API key redaction in error messages.
 *
 * This utility automatically detects and redacts API keys from error messages,
 * preventing sensitive credentials from being exposed in logs, crash reports,
 * and error dialogs.
 *
 * Supports redaction of:
 * - Standard API key patterns (e.g., sk-*, ak_*, etc.)
 * - Authorization headers with Bearer/Basic tokens
 * - URL query parameters containing keys
 * - Generic alphanumeric API key patterns
 * - Custom patterns defined by providers
 *
 * @property replacement The string to replace API keys with (default: "[REDACTED]")
 */
class ApiKeyRedaction(
    private val replacement: String = DEFAULT_REPLACEMENT
) {
    companion object {
        private const val TAG = "ApiKeyRedaction"
        private const val DEFAULT_REPLACEMENT = "[REDACTED]"
        
        // Common API key patterns
        // OpenAI: sk-xxxxxxxxxxxxxxxxxxxxxxxx
        private val OPENAI_KEY_PATTERN = Regex(
            "sk-[a-zA-Z0-9]{48}",
            RegexOption.IGNORE_CASE
        )
        
        // Anthropic: sk-ant-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
        private val ANTHROPIC_KEY_PATTERN = Regex(
            "sk-ant-[a-zA-Z0-9]{40,56}",
            RegexOption.IGNORE_CASE
        )
        
        // API key in Authorization header: Bearer sk-xxx...
        private val AUTHORIZATION_BEARER_PATTERN = Regex(
            "Bearer\\s+[a-zA-Z0-9_-]{20,}",
            RegexOption.IGNORE_CASE
        )
        
        // API key in Authorization header: Basic base64encoded
        private val AUTHORIZATION_BASIC_PATTERN = Regex(
            "Basic\\s+[a-zA-Z0-9+/=]{20,}",
            RegexOption.IGNORE_CASE
        )
        
        // Generic API key parameter: api_key=xxx or apikey=xxx
        private val API_KEY_PARAM_PATTERN = Regex(
            "(api[_-]?key|key|token|secret|password)=([^&\\s]+)",
            RegexOption.IGNORE_CASE
        )
        
        // Generic alphanumeric API key (hex/Base64-like patterns)
        private val GENERIC_KEY_PATTERN = Regex(
            "[a-f0-9]{32,64}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}",
            RegexOption.IGNORE_CASE
        )
        
        // URL with embedded credentials
        private val URL_WITH_AUTH_PATTERN = Regex(
            "(https?://)[^:/@]+:[^@]+@",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Redact API keys from a string.
     *
     * @param input The input string that may contain API keys
     * @return The string with all API keys redacted
     */
    fun redact(input: String): String {
        if (input.isEmpty()) return input
        
        var result = input
        var redactionCount = 0
        
        // Redact OpenAI-style keys
        result = OPENAI_KEY_PATTERN.replace(result) { match ->
            redactionCount++
            replacement
        }
        
        // Redact Anthropic-style keys
        result = ANTHROPIC_KEY_PATTERN.replace(result) { match ->
            redactionCount++
            replacement
        }
        
        // Redact Bearer tokens in Authorization headers
        result = AUTHORIZATION_BEARER_PATTERN.replace(result) { match ->
            redactionCount++
            "Bearer $replacement"
        }
        
        // Redact Basic auth in Authorization headers
        result = AUTHORIZATION_BASIC_PATTERN.replace(result) { match ->
            redactionCount++
            "Basic $replacement"
        }
        
        // Redact URL query parameter API keys
        result = API_KEY_PARAM_PATTERN.replace(result) { match ->
            val paramName = match.groupValues[1]
            redactionCount++
            "$paramName=$replacement"
        }
        
        // Redact URLs with embedded credentials
        result = URL_WITH_AUTH_PATTERN.replace(result) { match ->
            val protocol = match.groupValues[1]
            redactionCount++
            "$protocol$replacement@"
        }
        
        // Only redact generic UUID-like patterns if they look like API keys
        // (e.g., preceded by key/auth/token related words)
        val apiKeyContextPattern = Regex(
            "(api|key|token|secret|auth|credential)[_\\s:=]+([a-f0-9]{32,64}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})",
            RegexOption.IGNORE_CASE
        )
        result = apiKeyContextPattern.replace(result) { match ->
            val prefix = match.groupValues[1]
            redactionCount++
            "$prefix $replacement"
        }
        
        if (redactionCount > 0 && BuildConfig.DEBUG) {
            Log.d(TAG, "Redacted $redactionCount potential API key(s) from message")
        }
        
        return result
    }

    /**
     * Redact API keys from a Throwable's message.
     *
     * @param throwable The throwable to process
     * @return The throwable with its message redacted (creates a new exception)
     */
    fun redactThrowable(throwable: Throwable): Throwable {
        val redactedMessage = redact(throwable.message ?: "")
        
        return when (throwable) {
            is java.net.MalformedURLException -> java.net.MalformedURLException(redactedMessage)
            is java.io.IOException -> java.io.IOException(redactedMessage, throwable.cause)
            is java.lang.IllegalArgumentException -> java.lang.IllegalArgumentException(redactedMessage, throwable.cause)
            is java.lang.SecurityException -> java.lang.SecurityException(redactedMessage, throwable.cause)
            else -> RuntimeException(redactedMessage, throwable.cause)
        }.apply {
            // Preserve stack trace
            stackTrace = throwable.stackTrace
        }
    }

    /**
     * Check if a string contains what looks like an API key.
     *
     * @param input The input string to check
     * @return true if an API key pattern is detected
     */
    fun containsApiKey(input: String): Boolean {
        if (input.isEmpty()) return false
        
        return OPENAI_KEY_PATTERN.containsMatchIn(input) ||
               ANTHROPIC_KEY_PATTERN.containsMatchIn(input) ||
               AUTHORIZATION_BEARER_PATTERN.containsMatchIn(input) ||
               AUTHORIZATION_BASIC_PATTERN.containsMatchIn(input) ||
               API_KEY_PARAM_PATTERN.containsMatchIn(input) ||
               URL_WITH_AUTH_PATTERN.containsMatchIn(input)
    }
}

/**
 * Extension function to redact API keys from a string.
 */
fun String.redactApiKeys(): String = ApiKeyRedaction().redact(this)

/**
 * Extension function to check if a string contains API keys.
 */
fun String.containsApiKey(): Boolean = ApiKeyRedaction().containsApiKey(this)

/**
 * Extension function to create a redacted version of a Throwable.
 */
fun Throwable.withRedactedMessage(): Throwable = ApiKeyRedaction().redactThrowable(this)
