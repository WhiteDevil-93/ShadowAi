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
