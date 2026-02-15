package com.shadowai.app.ai

import com.shadowai.app.ui.chat.ChatMessage
import com.shadowai.app.ui.chat.ChatRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit tests for ConversationSummarizer.
 * Tests token counting, summary generation thresholds, and storage serialization.
 */
class ConversationSummarizerTest {

    @Mock
    private lateinit var mockModelManager: LlamaNative

    private lateinit var summarizer: ConversationSummarizer

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        summarizer = ConversationSummarizer(mockModelManager)
    }

    @Test
    fun `estimates token count from text`() {
        val text = "Hello world, this is a test."
        val tokens = summarizer.estimateTokenCount(text)

        // Rough estimate: ~4 characters per token
        val expected = (text.length / 4).coerceAtLeast(1)
        assertEquals(expected, tokens)
    }

    @Test
    fun `estimates token count for empty text`() {
        val text = ""
        val tokens = summarizer.estimateTokenCount(text)
        assertEquals(1, tokens) // Minimum 1 token
    }

    @Test
    fun `estimates context tokens for message list`() {
        val messages = listOf(
            createChatMessage("Hello", ChatRole.USER),
            createChatMessage("Hi there!", ChatRole.ASSISTANT),
            createChatMessage("How are you?", ChatRole.USER)
        )

        val estimatedTokens = summarizer.estimateContextTokens(messages)

        // Should be sum of individual message token estimates
        val expected = messages.sumOf { summarizer.estimateTokenCount(it.content) }
        assertEquals(expected, estimatedTokens)
    }

    @Test
    fun `should summarize returns false when disabled`() {
        val messages = createTestMessages(10)
        val maxContext = 4000

        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = false,
                threshold = 0.7f,
                targetTokens = 512,
                minMessagesToSummarize = 5
            )
        )

        val result = summarizer.shouldSummarize(messages, maxContext)

        assertFalse(result.shouldSummarize)
        assertTrue(result.recommendation?.contains("disabled") == true)
    }

    @Test
    fun `should summarize returns false when not enough messages`() {
        val messages = createTestMessages(3) // Less than min 5
        val maxContext = 4000

        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = true,
                threshold = 0.7f,
                targetTokens = 512,
                minMessagesToSummarize = 5
            )
        )

        val result = summarizer.shouldSummarize(messages, maxContext)

        assertFalse(result.shouldSummarize)
        assertTrue(result.recommendation?.contains("Not enough messages") == true)
    }

    @Test
    fun `should summarize returns true when threshold exceeded`() {
        // Create messages that exceed 70% of maxContext
        val messages = createLargeMessages(20, 400) // Each message ~100 tokens
        val maxContext = 2500 // 20 messages * ~100 tokens = 2000, which is > 70% of 2500

        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = true,
                threshold = 0.7f,
                targetTokens = 512,
                minMessagesToSummarize = 5
            )
        )

        val result = summarizer.shouldSummarize(messages, maxContext)

        assertTrue(result.shouldSummarize)
        assertTrue(result.currentUsage >= 0.7f)
    }

    @Test
    fun `should summarize returns false when below threshold`() {
        val messages = createTestMessages(5)
        val maxContext = 10000 // Large context, should be well below threshold

        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = true,
                threshold = 0.7f,
                targetTokens = 512,
                minMessagesToSummarize = 5
            )
        )

        val result = summarizer.shouldSummarize(messages, maxContext)

        assertFalse(result.shouldSummarize)
        assertTrue(result.currentUsage < 0.7f)
    }

    @Test
    fun `summarizes conversation successfully`() = runTest {
        val messages = createTestMessages(10)
        val maxContext = 4000

        val result = summarizer.summarizeConversation(messages, maxContext)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()
        assertNotNull(summary)
        assertTrue(summary!!.metadata.messageCount > 0)
        assertTrue(summary.content.isNotEmpty())
    }

    @Test
    fun `summarize conversation returns failure for empty messages`() = runTest {
        val messages = emptyList<ChatMessage>()
        val maxContext = 4000

        val result = summarizer.summarizeConversation(messages, maxContext)

        assertTrue(result.isFailure)
    }

    @Test
    fun `summary includes metadata`() = runTest {
        val messages = createTestMessages(10)
        val maxContext = 4000

        val result = summarizer.summarizeConversation(messages, maxContext)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()
        assertNotNull(summary?.metadata)
        assertEquals(10, summary?.metadata?.messageCount)
        assertTrue(summary?.metadata?.createdAt!! > 0)
        assertNotNull(summary.metadata.id)
    }

    @Test
    fun `summary serialization generates valid JSON`() = runTest {
        val messages = createTestMessages(10)
        val maxContext = 4000

        val result = summarizer.summarizeConversation(messages, maxContext)
        assertTrue(result.isSuccess)

        val summary = result.getOrNull()
        val json = summary?.toJson()

        assertNotNull(json)
        assertTrue(json!!.isNotEmpty())
        assertTrue(json.contains("metadata"))
        assertTrue(json.contains("content"))
    }

    @Test
    fun `summary respects min messages to summarize threshold`() = runTest {
        val messages = createTestMessages(3) // Less than min 5
        val maxContext = 4000

        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = true,
                threshold = 0.5f,
                targetTokens = 512,
                minMessagesToSummarize = 5
            )
        )

        val result = summarizer.summarizeConversation(messages, maxContext)

        assertTrue(result.isFailure) // Should fail with "No messages qualify for summarization"
    }

    @Test
    fun `create simple summary when model unavailable`() = runTest {
        val messages = createTestMessages(10)
        val maxContext = 4000

        val result = summarizer.summarizeConversation(messages, maxContext)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()
        assertNotNull(summary)

        // Simple summary should contain conversation details
        val content = summary!!.content
        assertTrue(content.contains("Conversation Summary"))
        assertTrue(content.contains("user messages"))
        assertTrue(content.contains("assistant responses"))
    }

    @Test
    fun `update config changes summarization behavior`() {
        val messages = createTestMessages(10)
        val maxContext = 4000

        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = true,
                threshold = 0.5f, // Lower threshold
                targetTokens = 256,
                minMessagesToSummarize = 3
            )
        )

        val result = summarizer.shouldSummarize(messages, maxContext)

        // With threshold at 0.5, should summarize sooner
        assertEquals(0.5f, result.threshold)
    }

    @Test
    fun `has summary detection works`() {
        val messagesWithSummary = listOf(
            createChatMessage("Hello", ChatRole.USER),
            createChatMessage("Hi", ChatRole.ASSISTANT, mapOf("summary_id" to "123"))
        )

        assertTrue(summarizer.hasSummary(messagesWithSummary))

        val messagesWithoutSummary = listOf(
            createChatMessage("Hello", ChatRole.USER),
            createChatMessage("Hi", ChatRole.ASSISTANT)
        )

        assertFalse(summarizer.hasSummary(messagesWithoutSummary))
    }

    @Test
    fun `get summary id from message metadata`() {
        val message = createChatMessage("test", ChatRole.USER, mapOf("summary_id" to "abc123"))
        val summaryId = summarizer.getSummaryId(message)

        assertEquals("abc123", summaryId)
    }

    @Test
    fun `get summary id returns null when not present`() {
        val message = createChatMessage("test", ChatRole.USER, emptyMap())
        val summaryId = summarizer.getSummaryId(message)

        assertNull(summaryId)
    }

    @Test
    fun `create summary placeholder generates valid message`() = runTest {
        val messages = createTestMessages(10)
        val maxContext = 4000

        val result = summarizer.summarizeConversation(messages, maxContext)
        assertTrue(result.isSuccess)

        val summary = result.getOrNull()
        val placeholder = summarizer.createSummaryPlaceholder(summary!!)

        assertEquals(ChatRole.SYSTEM, placeholder.role)
        assertTrue(placeholder.metadata?.containsKey("summary_id") == true)
        assertTrue(placeholder.content.contains("summarized"))
    }

    @Test
    fun `token counting handles different text lengths`() {
        val shortText = "Hi"
        val longText = "A".repeat(1000)

        val shortTokens = summarizer.estimateTokenCount(shortText)
        val longTokens = summarizer.estimateTokenCount(longText)

        assertTrue(longTokens > shortTokens)
    }

    // ========== Helper Methods ==========

    private fun createChatMessage(
        content: String,
        role: ChatRole,
        metadata: Map<String, String> = emptyMap()
    ): ChatMessage {
        return ChatMessage(
            id = "msg-${System.currentTimeMillis()}-${(0..1000).random()}",
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )
    }

    private fun createTestMessages(count: Int): List<ChatMessage> {
        return (0 until count).map { index ->
            val role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT
            val content = "This is message number $index with some sample content to simulate a realistic conversation."
            createChatMessage(content, role)
        }
    }

    private fun createLargeMessages(count: Int, avgLength: Int): List<ChatMessage> {
        return (0 until count).map { index ->
            val role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT
            val content = "A".repeat(avgLength) + " message $index"
            createChatMessage(content, role)
        }
    }
}