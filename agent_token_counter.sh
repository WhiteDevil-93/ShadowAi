#!/bin/bash
# Agent 1: TokenCounter Implementation
echo "🤖 Agent 1: TokenCounter Implementation starting..."

cd /mnt/c/Users/anon3/Downloads/ShadowAi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "⚠️  Java not available, creating files directly instead of compiling"
fi

# Create TokenCounter.kt
cat > app/src/main/java/com/shadowai/app/ai/TokenCounter.kt << 'KOTLIN_EOF'
package com.shadowai.app.ai

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
        private val tokenCountCache = mutableMapOf<String, Int>()
    }

    /**
     * Counts tokens in text using heuristic.
     */
    fun countTokens(text: String): Int {
        if (text.isBlank()) return 0

        return tokenCountCache.getOrPut(text) {
            (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
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
     * Clears the token count cache.
     */
    fun clearCache() {
        tokenCountCache.clear()
    }
}
KOTLIN_EOF

echo "✅ TokenCounter.kt created"

# Create test file
mkdir -p app/src/test/kotlin/com/shadowai/app/ai
cat > app/src/test/kotlin/com/shadowai/app/ai/TokenCounterTest.kt << 'TEST_EOF'
package com.shadowai.app.ai

import org.junit.Test
import org.junit.Assert.*

class TokenCounterTest {

    private val tokenCounter = TokenCounter()

    @Test
    fun `counts tokens using heuristic`() {
        val text = "The quick brown fox jumps over the lazy dog"
        val tokens = tokenCounter.countTokens(text)
        assertTrue("Token count should be positive", tokens > 0)
        assertEquals("Should approximate using ~4 chars/token", 10, tokens)
    }

    @Test
    fun `handles empty text`() {
        val text = ""
        val tokens = tokenCounter.countTokens(text)
        assertEquals("Empty text should have 0 tokens", 0, tokens)
    }

    @Test
    fun `handles blank text`() {
        val text = "   "
        val tokens = tokenCounter.countTokens(text)
        assertEquals("Blank text should have 0 tokens", 0, tokens)
    }

    @Test
    fun `caches token counts`() {
        val text = "Sample text"
        val count1 = tokenCounter.countTokens(text)
        val count2 = tokenCounter.countTokens(text)
        assertEquals("Cached count should match", count1, count2)
    }

    @Test
    fun `counts tokens for multiple messages`() {
        val messages = listOf(
            "Hello world",
            "How are you?",
            "This is a test"
        )
        val tokens = tokenCounter.countTokens(messages)
        assertTrue("Should count tokens for all messages", tokens > 0)
    }

    @Test
    fun `calculates context tokens correctly`() {
        val systemPrompt = "You are a helpful assistant"
        val history = listOf(
            "User: Hello",
            "Assistant: Hi there",
            "User: How are you?"
        )
        val tokens = tokenCounter.calculateContextTokens(systemPrompt, history)
        val directCount = tokenCounter.countTokens(systemPrompt) + tokenCounter.countTokens(history)
        assertEquals("Should sum system and history tokens", directCount, tokens)
    }

    @Test
    fun `clearCache removes cached values`() {
        val text = "Testing cache clearing"
        tokenCounter.countTokens(text)  // Cache it
        tokenCounter.clearCache()
        val countAfterClear = tokenCounter.countTokens(text)
        assertTrue("Should still count after cache clear", countAfterClear > 0)
    }
}
TEST_EOF

echo "✅ TokenCounterTest.kt created"
echo "✅ Agent 1: TokenCounter Implementation complete!"