package com.shadowai.core.utils

/**
 * Utility for estimating token counts and managing context windows.
 * In production, this would use a real tokenizer like TikToken,
 * but here we use a standard heuristic of ~4 characters per token.
 */
object TokenCounter {
    
    // TikToken-style estimation for English text
    private const val CHARS_PER_TOKEN = 4.0

    /**
     * Estimates the number of tokens in a given text.
     */
    fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return Math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    /**
     * Truncates a list of strings (e.g., chat exchanges) to fit within a token limit
     * using a sliding window approach, keeping the most recent items.
     * 
     * @param items The list of strings to truncate.
     * @param maxTokens The maximum allowed tokens.
     * @param reservedTokens Tokens to reserve for model instructions or other overhead.
     * @return A truncated list of strings that fits within the limit.
     */
    fun getSlidingWindow(
        items: List<String>,
        maxTokens: Int,
        reservedTokens: Int = 0
    ): List<String> {
        val availableTokens = (maxTokens - reservedTokens).coerceAtLeast(0)
        if (availableTokens == 0) return emptyList()

        val result = mutableListOf<String>()
        var currentTokenCount = 0

        // Iterate from most recent to oldest
        for (item in items.reversed()) {
            val itemTokens = countTokens(item)
            if (currentTokenCount + itemTokens <= availableTokens) {
                result.add(0, item)
                currentTokenCount += itemTokens
            } else {
                break
            }
        }

        return result
    }

    /**
     * Checks if the total token count exceeds a certain threshold of the context size.
     */
    fun isNearingCapacity(text: String, capacity: Int, threshold: Float = 0.9f): Boolean {
        val count = countTokens(text)
        return count >= capacity * threshold
    }
}
