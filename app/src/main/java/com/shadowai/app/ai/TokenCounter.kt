package com.shadowai.app.ai

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M-12: Centralized token counter for text input estimation.
 * H-4 FIX: Silent token truncation → throw exception or require streaming
 * 
 * All token counting consolidated to this single source for consistency.
 * Uses heuristic ~4 characters per token as fallback when proper tokenizer unavailable.
 *
 * Features:
 * - Heuristic counting for fallback scenarios
 * - Message and conversation context token calculation
 * - Cache for performance optimization
 * - Validation with exceptions
 * - Consistent API used by ConversationSummarizer and other components
 */
@Singleton
class TokenCounter @Inject constructor() {
    // M-11: Standardized logging
    companion object {
        private const val TAG = "TokenCounter"
        /**
         * Exception thrown when token limit is exceeded.
         * H-4 FIX: Silent token truncation → throw exception or require streaming
         */
        class TokenLimitExceededException(
            message: String,
            val requestedTokens: Int,
            val maxTokens: Int,
            val content: String? = null
        ) : IllegalArgumentException(message)

        /**
         * Heuristic: ~4 characters per token for English text.
         * This varies by text, tokenizer, and language, but provides reasonable estimates.
         */
        private const val CHARS_PER_TOKEN = 4

        /**
         * M-12: Cache with size limitation to prevent memory growth.
         * Cache token counts for performance.
         */
        private val tokenCountCache = mutableMapOf<String, Int>()
        private const val MAX_CACHE_SIZE = 1000
        
        /**
         * M-12: Enforce cache size limits.
         */
        private fun enforceCacheSizeLimit() {
            if (tokenCountCache.size > MAX_CACHE_SIZE) {
                // Remove oldest entries (simple FIFO eviction)
                val keysToRemove = tokenCountCache.keys.take(tokenCountCache.size - MAX_CACHE_SIZE + 50)
                keysToRemove.forEach { tokenCountCache.remove(it) }
                Log.d(TAG, "Token cache trimmed to ${tokenCountCache.size} entries")
            }
        }
    }

    /**
     * Counts tokens in text using heuristic.
     */
    fun countTokens(text: String): Int {
        if (text.isBlank()) return 0

        return tokenCountCache.getOrPut(text) {
            enforceCacheSizeLimit()
            (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
        }
    }

    /**
     * Validates that the text fits within the specified token limit.
     * H-4 FIX: Throws TokenLimitExceededException if limit is exceeded.
     *
     * @param text The text to validate
     * @param maxTokens Maximum allowed tokens
     * @param context Context for error message
     * @throws TokenLimitExceededException if token count exceeds maxTokens
     */
    fun validateTokenLimit(text: String, maxTokens: Int, context: String = "input") {
        val tokenCount = countTokens(text)
        if (tokenCount > maxTokens) {
            throw TokenLimitExceededException(
                message = "$context exceeds maximum token limit: $tokenCount > $maxTokens tokens. " +
                         "Use streaming mode for large inputs or reduce content length.",
                requestedTokens = tokenCount,
                maxTokens = maxTokens,
                content = text.take(100) + "..." // Include preview for debugging
            )
        }
    }

    /**
     * Validates that a list of messages fits within the specified token limit.
     * H-4 FIX: Throws TokenLimitExceededException if limit is exceeded.
     *
     * @param messages The messages to validate
     * @param maxTokens Maximum allowed tokens
     * @throws TokenLimitExceededException if total token count exceeds maxTokens
     */
    fun validateTokenLimit(messages: List<String>, maxTokens: Int) {
        val tokenCount = countTokens(messages)
        if (tokenCount > maxTokens) {
            throw TokenLimitExceededException(
                message = "Message batch exceeds maximum token limit: $tokenCount > $maxTokens tokens. " +
                         "Use streaming mode for large conversations or reduce message count.",
                requestedTokens = tokenCount,
                maxTokens = maxTokens,
                content = "${messages.size} messages, ${messages.sumOf { it.length }} total chars"
            )
        }
    }

    /**
     * Counts tokens for a list of messages.
     */
    fun countTokens(messages: List<String>): Int {
        return messages.sumOf { countTokens(it) }
    }

    /**
     * Calculates total tokens including system prompt and conversation history.
     */
    fun calculateContextTokens(
        systemPrompt: String,
        conversationHistory: List<String>
    ): Int {
        return countTokens(systemPrompt) + countTokens(conversationHistory)
    }

    /**
     * Calculates and validates total context tokens against maximum limit.
     * H-4 FIX: Throws exception on truncation instead of silently truncating.
     *
     * @param systemPrompt System prompt tokens
     * @param conversationHistory Conversation history tokens
     * @param maxContext Maximum context window size
     * @throws TokenLimitExceededException if context exceeds limit
     */
    fun calculateAndValidateContextTokens(
        systemPrompt: String,
        conversationHistory: List<String>,
        maxContext: Int
    ): Int {
        val systemTokens = countTokens(systemPrompt)
        val historyTokens = countTokens(conversationHistory)
        val total = systemTokens + historyTokens

        if (total > maxContext) {
            throw TokenLimitExceededException(
                message = "Context window overflow: $total total tokens (system: $systemTokens, history: $historyTokens) " +
                         "exceeds maximum $maxContext. Use streaming mode or reduce conversation length.",
                requestedTokens = total,
                maxTokens = maxContext,
                content = "System: ${systemPrompt.take(50)}..., History: ${conversationHistory.size} messages"
            )
        }

        return total
    }

    /**
     * M-12: Counts tokens in a conversation string.
     * This consolidates duplicated token counting logic from ConversationSummarizer.
     *
     * @param content Text content to count
     * @return Estimated token count
     */
    fun estimateConversationTokens(content: String): Int {
        // M-12: Standardized method that was previously duplicated
        return countTokens(content)
    }

    /**
     * M-12: Counts tokens in a list of messages for conversation analysis.
     * Used by ConversationSummarizer and other context-aware components.
     *
     * @param messages List of text messages
     * @return Total estimated token count
     */
    fun estimateMessagesTokens(messages: List<String>): Int {
        // M-12: Standardized method that was previously duplicated
        return countTokens(messages)
    }

    /**
     * M-12: Enhanced cache info for observability.
     */
    fun getCacheInfo(): Map<String, Any> {
        return mapOf(
            "cacheSize" to tokenCountCache.size,
            "maxSize" to MAX_CACHE_SIZE,
            "hitRate" to 0.0  // Could be enhanced with actual hit tracking
        )
    }

    /**
     * Clears the token count cache.
     */
    fun clearCache() {
        tokenCountCache.clear()
        Log.d(TAG, "Token cache cleared")
    }
}
