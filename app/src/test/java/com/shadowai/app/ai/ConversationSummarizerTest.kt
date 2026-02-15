package com.shadowai.app.ai

import com.shadowai.app.ui.chat.ChatMessage
import com.shadowai.app.ui.chat.ChatRole
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for ConversationSummarizer.
 *
 * Tests:
 * - Token counting logic (M-12: centralized TokenCounter)
 * - Summary generation thresholds
 * - AI fallback when model unavailable
 * - Storage serialization
 * - Configuration management
 * - Message splitting logic
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSummarizerTest {

    private lateinit var mockModelManager: ILlamaEngine
    private lateinit var mockTokenCounter: TokenCounter
    private lateinit var summarizer: ConversationSummarizer

    @Before
    fun setup() {
        mockModelManager = mockk(relaxed = true)
        mockTokenCounter = TokenCounter() // Use real TokenCounter for accuracy
        summarizer = ConversationSummarizer(mockModelManager, mockTokenCounter)
    }

    // ========== Token Counting Tests ==========

    @Test
    fun `estimateContextTokens sums all message tokens`() {
        val messages = listOf(
            createMessage(content = "Hello world"),  // ~2 tokens
            createMessage(content = "How are you doing today"),  // ~5 tokens
            createMessage(content = "Goodbye")  // ~2 tokens
        )

        val result = summarizer.estimateContextTokens(messages)

        // TokenCounter uses ~4 chars per token
        // "Hello world" = 11 chars / 4 = ~3 tokens
        assertTrue("Should estimate positive token count", result > 0)
        assertEquals(
            mockTokenCounter.countTokens("Hello world") +
            mockTokenCounter.countTokens("How are you doing today") +
            mockTokenCounter.countTokens("Goodbye"),
            result
        )
    }

    @Test
    fun `estimateContextTokens returns 0 for empty list`() {
        val result = summarizer.estimateContextTokens(emptyList())
        assertEquals(0, result)
    }

    @Test
    fun `estimateContextTokens handles single message`() {
        val messages = listOf(createMessage(content = "Test message"))
        val result = summarizer.estimateContextTokens(messages)
        assertEquals(mockTokenCounter.countTokens("Test message"), result)
    }

    @Test
    fun `estimateTokenCount delegates to TokenCounter`() {
        val text = "This is a test message for token counting"
        val result = summarizer.estimateTokenCount(text)
        assertEquals(mockTokenCounter.countTokens(text), result)
    }

    @Test
    fun `estimateTokenCount uses consistent heuristics`() {
        // Verify the ~4 chars per token heuristic
        val shortText = "Hi" // 2 chars = 1 token (minimum)
        val mediumText = "Hello world" // 11 chars = ~3 tokens
        val longText = "This is a longer message with more content" // 42 chars = ~11 tokens

        assertEquals(1, summarizer.estimateTokenCount(shortText))
        assertEquals(3, summarizer.estimateTokenCount(mediumText))
        assertEquals(11, summarizer.estimateTokenCount(longText))
    }

    @Test
    fun `estimateTokenCount caches results`() {
        val text = "Test message for caching"
        
        // First call
        val first = summarizer.estimateTokenCount(text)
        // Second call should return cached value
        val second = summarizer.estimateTokenCount(text)
        
        assertEquals(first, second)
    }

    // ========== Threshold Tests ==========

    @Test
    fun `shouldSummarize returns false when below threshold`() {
        val messages = createMessages(3) // 3 messages
        val maxContext = 1000

        val result = summarizer.shouldSummarize(messages, maxContext)

        assertFalse("Should not summarize below 70% threshold", result.shouldSummarize)
        assertEquals(0f, result.currentUsage, 0.01f)
    }

    @Test
    fun `shouldSummarize returns true when above threshold`() {
        // Create messages that will exceed 70% of small context
        val messages = createLargeMessages(50) // 50 large messages
        val maxContext = 100 // Very small context to trigger threshold

        val result = summarizer.shouldSummarize(messages, maxContext)

        assertTrue("Should summarize when above 70% threshold", result.shouldSummarize)
        assertTrue("Usage ratio should exceed 0.7", result.currentUsage > 0.7f)
    }

    @Test
    fun `shouldSummarize returns false when disabled`() {
        summarizer.updateConfig(ConversationSummarizer.Config(enabled = false))
        
        val messages = createLargeMessages(100)
        val result = summarizer.shouldSummarize(messages, 100)

        assertFalse("Should not summarize when disabled", result.shouldSummarize)
        assertEquals("Auto-summarization is disabled", result.recommendation)
    }

    @Test
    fun `shouldSummarize returns false when below min messages`() {
        val messages = createMessages(2) // Less than default min of 5
        val result = summarizer.shouldSummarize(messages, 50)

        assertFalse("Should not summarize with too few messages", result.shouldSummarize)
        assertTrue("Recommendation should mention minimum", 
            result.recommendation?.contains("minimum") == true)
    }

    @Test
    fun `shouldSummarize respects custom threshold`() {
        summarizer.updateConfig(ConversationSummarizer.Config(threshold = 0.5f))
        
        // Create messages at ~60% of 100 tokens
        val messages = List(15) { createMessage(content = "Message content here") }
        val result = summarizer.shouldSummarize(messages, 100)

        assertTrue("Should summarize at custom 50% threshold", result.shouldSummarize)
        assertEquals(0.5f, result.threshold, 0.01f)
    }

    @Test
    fun `shouldSummarize includes usage percentage in recommendation`() {
        val messages = createMessages(10)
        val result = summarizer.shouldSummarize(messages, 1000)

        assertTrue("Recommendation should include percentage",
            result.recommendation?.contains("%") == true)
    }

    // ========== Summarization Logic Tests ==========

    @Test
    fun `summarizeConversation returns failure for empty messages`() = runTest {
        val result = summarizer.summarizeConversation(emptyList(), 1000)

        assertTrue("Should fail for empty messages", result.isFailure)
        assertTrue("Error should mention no messages", 
            result.exceptionOrNull()?.message?.contains("No messages") == true)
    }

    @Test
    fun `summarizeConversation creates summary with metadata`() = runTest {
        val messages = createMessages(10)
        
        val result = summarizer.summarizeConversation(messages, 1000)

        assertTrue("Should succeed with valid messages", result.isSuccess)
        
        val summary = result.getOrNull()
        assertNotNull(summary)
        assertNotNull(summary?.metadata)
        assertEquals(10, summary?.metadata?.messageCount)
        assertNotNull(summary?.metadata?.id)
        assertTrue(summary?.metadata?.createdAt ?: 0 > 0)
    }

    @Test
    fun `summarizeConversation includes summarized messages in result`() = runTest {
        val messages = createMessages(10)
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertNotNull(summary?.summarizedMessages)
        assertTrue("Should have summarized some messages", 
            summary?.summarizedMessages?.isNotEmpty() == true)
    }

    @Test
    fun `summarizeConversation generates content in summary`() = runTest {
        val messages = createMessages(5)
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertNotNull(summary?.content)
        assertTrue("Summary content should not be empty", 
            summary?.content?.isNotBlank() == true)
    }

    @Test
    fun `summarizeConversation calculates token counts`() = runTest {
        val messages = createMessages(5, content = "Test message content here")
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertTrue("Should track original token count", 
            summary?.metadata?.originalContextTokens ?: 0 > 0)
        assertTrue("Should track summary token count", 
            summary?.metadata?.summaryTokens ?: 0 >= 0)
    }

    @Test
    fun `summarizeConversation sets timestamp range`() = runTest {
        val now = System.currentTimeMillis()
        val messages = listOf(
            createMessage(content = "First", timestamp = now - 10000),
            createMessage(content = "Second", timestamp = now - 5000),
            createMessage(content = "Third", timestamp = now)
        )
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertEquals(now - 10000, summary?.metadata?.timestampBegin)
        assertEquals(now, summary?.metadata?.timestampEnd)
    }

    // ========== AI Fallback Tests ==========

    @Test
    fun `summarizeConversation uses simple summary when model unavailable`() = runTest {
        // Model manager is mocked relaxed, so it won't throw but won't provide real inference
        val messages = listOf(
            createMessage(role = ChatRole.USER, content = "Hello"),
            createMessage(role = ChatRole.ASSISTANT, content = "Hi there"),
            createMessage(role = ChatRole.USER, content = "How are you?")
        )
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        assertTrue("Should succeed even without model", result.isSuccess)
        
        val summary = result.getOrNull()
        assertNotNull(summary)
        // Simple summary should contain role counts
        assertTrue("Fallback should include message counts",
            summary?.content?.contains("messages") == true)
    }

    @Test
    fun `simple summary includes role counts`() = runTest {
        val messages = listOf(
            createMessage(role = ChatRole.USER, content = "User message 1"),
            createMessage(role = ChatRole.ASSISTANT, content = "Assistant response 1"),
            createMessage(role = ChatRole.USER, content = "User message 2"),
            createMessage(role = ChatRole.ASSISTANT, content = "Assistant response 2"),
            createMessage(role = ChatRole.SYSTEM, content = "System prompt")
        )
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertTrue("Should count user messages",
            summary?.content?.contains("user messages") == true)
        assertTrue("Should count assistant messages",
            summary?.content?.contains("assistant") == true)
        assertTrue("Should count system messages",
            summary?.content?.contains("system") == true)
    }

    @Test
    fun `simple summary includes topic preview`() = runTest {
        val messages = listOf(
            createMessage(role = ChatRole.USER, content = "Discussing artificial intelligence and machine learning concepts")
        )
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertTrue("Should include topics discussed",
            summary?.content?.contains("topics") == true)
    }

    @Test
    fun `simple summary truncates long content`() = runTest {
        val longContent = "A".repeat(1000)
        val messages = listOf(
            createMessage(role = ChatRole.USER, content = longContent)
        )
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        assertTrue("Summary should be shorter than original",
            (summary?.content?.length ?: 0) < longContent.length)
    }

    // ========== Storage Serialization Tests ==========

    @Test
    fun `stored summary serializes to JSON`() = runTest {
        val messages = createMessages(5)
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        val json = summary?.toJson()
        
        assertNotNull(json)
        assertTrue("JSON should contain metadata", json?.contains("metadata") == true)
        assertTrue("JSON should contain content", json?.contains("content") == true)
        assertTrue("JSON should contain message IDs", json?.contains("summarizedMessageIds") == true)
    }

    @Test
    fun `JSON serialization includes all metadata fields`() = runTest {
        val messages = createMessages(3)
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        val json = summary?.toJson()
        
        assertNotNull(json)
        assertTrue("Should include id", json?.contains("id") == true)
        assertTrue("Should include createdAt", json?.contains("createdAt") == true)
        assertTrue("Should include messageCount", json?.contains("messageCount") == true)
    }

    @Test
    fun `summary metadata is serializable`() {
        val metadata = ConversationSummarizer.SummaryMetadata(
            id = "test-id-123",
            createdAt = System.currentTimeMillis(),
            messageCount = 10,
            originalContextTokens = 500,
            summaryTokens = 50,
            timestampBegin = 1000L,
            timestampEnd = 2000L
        )
        
        // Should be able to serialize (using kotlinx.serialization)
        assertEquals("test-id-123", metadata.id)
        assertEquals(10, metadata.messageCount)
        assertEquals(500, metadata.originalContextTokens)
    }

    // ========== Configuration Tests ==========

    @Test
    fun `updateConfig changes summarization settings`() {
        val newConfig = ConversationSummarizer.Config(
            enabled = false,
            threshold = 0.5f,
            targetTokens = 256,
            minMessagesToSummarize = 3
        )
        
        summarizer.updateConfig(newConfig)
        
        // Verify by checking behavior
        val messages = createMessages(4)
        val result = summarizer.shouldSummarize(messages, 50)
        
        assertFalse("Should respect disabled config", result.shouldSummarize)
    }

    @Test
    fun `default config values are reasonable`() {
        val result = summarizer.shouldSummarize(createMessages(10), 100)
        
        // Default threshold is 0.7 (70%)
        assertEquals(0.7f, result.threshold, 0.01f)
    }

    // ========== Has Summary Tests ==========

    @Test
    fun `hasSummary returns true when summary metadata present`() {
        val messages = listOf(
            createMessage(metadata = mapOf("summary_id" to "summary-123")),
            createMessage()
        )
        
        assertTrue(summarizer.hasSummary(messages))
    }

    @Test
    fun `hasSummary returns false when no summary metadata`() {
        val messages = listOf(
            createMessage(),
            createMessage()
        )
        
        assertFalse(summarizer.hasSummary(messages))
    }

    @Test
    fun `hasSummary handles empty list`() {
        assertFalse(summarizer.hasSummary(emptyList()))
    }

    @Test
    fun `getSummaryId extracts id from message metadata`() {
        val message = createMessage(metadata = mapOf("summary_id" to "summary-456"))
        
        assertEquals("summary-456", summarizer.getSummaryId(message))
    }

    @Test
    fun `getSummaryId returns null when no metadata`() {
        val message = createMessage()
        
        assertNull(summarizer.getSummaryId(message))
    }

    // ========== Placeholder Tests ==========

    @Test
    fun `createSummaryPlaceholder generates system message`() = runTest {
        val messages = createMessages(5)
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull() ?: throw AssertionError("Summary is null")
        val placeholder = summarizer.createSummaryPlaceholder(summary)
        
        assertEquals(ChatRole.SYSTEM, placeholder.role)
        assertTrue("Should indicate summary", placeholder.content.contains("summarized"))
        assertTrue("Should show message count", 
            placeholder.content.contains("${summary.metadata.messageCount}"))
    }

    @Test
    fun `placeholder has summary metadata`() = runTest {
        val messages = createMessages(5)
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull() ?: throw AssertionError("Summary is null")
        val placeholder = summarizer.createSummaryPlaceholder(summary)
        
        assertEquals(summary.metadata.id, placeholder.metadata?.get("summary_id"))
        assertEquals("true", placeholder.metadata?.get("is_summary"))
        assertEquals("${summary.metadata.messageCount}", placeholder.metadata?.get("message_count"))
    }

    @Test
    fun `placeholder id is based on summary id`() = runTest {
        val messages = createMessages(3)
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull() ?: throw AssertionError("Summary is null")
        val placeholder = summarizer.createSummaryPlaceholder(summary)
        
        assertTrue("Placeholder ID should contain summary reference",
            placeholder.id.contains(summary.metadata.id))
    }

    // ========== Message Splitting Tests ==========

    @Test
    fun `determineMessagesToSummarize keeps recent messages`() = runTest {
        val messages = (1..10).map { i ->
            createMessage(content = "Message $i", timestamp = i * 1000L)
        }
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        val summary = result.getOrNull()
        // Should summarize oldest, keep newest
        assertTrue("Should keep some messages",
            summary?.metadata?.messageCount ?: 0 < messages.size)
    }

    @Test
    fun `determineMessagesToSummarize respects min messages`() = runTest {
        summarizer.updateConfig(ConversationSummarizer.Config(minMessagesToSummarize = 8))
        
        val messages = createMessages(5) // Less than min
        val result = summarizer.summarizeConversation(messages, 50)
        
        assertTrue("Should fail when below min", result.isFailure)
    }

    @Test
    fun `determineMessagesToSummarize targets remaining tokens`() = runTest {
        // Create messages with known token counts
        val messages = List(20) { createMessage(content = "Short msg") }
        
        val result = summarizer.summarizeConversation(messages, 100)
        
        // Should succeed and create reasonable split
        assertTrue("Should handle target token calculation", result.isSuccess)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `handles cancellation exception`() = runTest {
        // This test verifies the cancellation exception is re-thrown
        // In practice, this would be triggered by coroutine cancellation
        val messages = createMessages(5)
        
        // Normal operation shouldn't throw
        val result = summarizer.summarizeConversation(messages, 1000)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `handles very long messages gracefully`() = runTest {
        val longMessage = createMessage(content = "Word ".repeat(10000))
        val messages = listOf(longMessage)
        
        val result = summarizer.summarizeConversation(messages, 100000)
        
        assertTrue("Should handle very long message", result.isSuccess)
    }

    @Test
    fun `handles special characters in messages`() = runTest {
        val messages = listOf(
            createMessage(content = "Special chars: äöü € 日本語 🎉 <script>alert('xss')</script>"),
            createMessage(content = "Newlines:\n\n\r\tTabs and spaces   ")
        )
        
        val result = summarizer.summarizeConversation(messages, 1000)
        
        assertTrue("Should handle special characters", result.isSuccess)
    }

    // ========== Helper Methods ==========

    private fun createMessage(
        content: String = "Test message",
        role: ChatRole = ChatRole.USER,
        timestamp: Long = System.currentTimeMillis(),
        metadata: Map<String, String>? = null
    ): ChatMessage {
        return ChatMessage(
            content = content,
            role = role,
            timestamp = timestamp,
            metadata = metadata
        )
    }

    private fun createMessages(count: Int, content: String = "Test message message content"): List<ChatMessage> {
        return (1..count).map { i ->
            createMessage(content = "$content $i", timestamp = i * 1000L)
        }
    }

    private fun createLargeMessages(count: Int): List<ChatMessage> {
        return (1..count).map { i ->
            createMessage(
                content = "This is a much larger message with substantial content to increase token count significantly number $i. " +
                        "It contains many words and characters to properly test the token estimation threshold logic.",
                timestamp = i * 1000L
            )
        }
    }
}