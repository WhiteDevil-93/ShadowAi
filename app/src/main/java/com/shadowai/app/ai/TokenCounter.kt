package com.shadowai.app.ai

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token counter for text input estimation.
 * Uses heuristic ~4 characters per token as fallback when proper tokenizer unavailable.
 */
@Singleton
class TokenCounter @Inject constructor() {

    companion object {
        /**
         * Heuristic: ~4 characters per token for English text.
         * This varies by text, tokenizer, and language, but provides reasonable estimates.
         */
        private const val CHARS_PER_TOKEN = 4

        /**
         * Cache token counts for performance.
         */
        private val tokenCountCache = ConcurrentHashMap<String, Int>()
        private const val MAX_CACHE_ENTRIES = 2048
    }

    /**
     * Counts tokens in text using heuristic.
     */
    fun countTokens(text: String): Int {
        if (text.isBlank()) return 0

        tokenCountCache[text]?.let { return it }
        val estimate = (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)

        // Keep cache bounded to avoid unbounded growth when prompts vary heavily.
        if (tokenCountCache.size >= MAX_CACHE_ENTRIES) {
            tokenCountCache.clear()
        }
        tokenCountCache[text] = estimate
        return estimate
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
     * Clears the token count cache.
     */
    fun clearCache() {
        tokenCountCache.clear()
    }
}
