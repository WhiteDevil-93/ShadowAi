package com.shadowai.app.agent

import com.shadowai.app.ai.TokenCounter
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.Capability
import com.shadowai.core.ProviderId
import org.junit.Test
import org.junit.Assert.*

class ContextManagerTest {

    private val tokenCounter = TokenCounter()
    private val contextManager = ContextManager(tokenCounter)

    private fun createTestModel(maxContext: Int = 4096): ModelDescriptor {
        return ModelDescriptor(
            id = "test-model",
            name = "Test Model",
            providerId = ProviderId.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT),
            maxContext = maxContext
        )
    }

    @Test
    fun `preserves context when within limits`() {
        val model = createTestModel(maxContext = 4096)
        val exchanges = listOf(
            ContextManager.ConversationExchange("user", "Hello"),
            ContextManager.ConversationExchange("assistant", "Hi there")
        )

        val result = contextManager.manageContext(
            exchanges = exchanges,
            systemPrompt = "You are helpful.",
            modelDescriptor = model
        )

        assertFalse("Should not truncate when within limits", result.wasTruncated)
        assertEquals("Should preserve all exchanges", 2, result.exchanges.size)
        assertEquals(result.originalTokens, result.truncatedTokens)
    }

    @Test
    fun `applies sliding window when over threshold`() {
        val model = createTestModel(maxContext = 100)  // Small context for testing
        val exchanges = (1..20).map {
            ContextManager.ConversationExchange("user", "Message $it")
        }

        val result = contextManager.manageContext(
            exchanges = exchanges,
            systemPrompt = "System",
            modelDescriptor = model,
            windowSize = 3
        )

        assertTrue("Should truncate when over threshold", result.wasTruncated)
        assertTrue("Should limit to window size", result.exchanges.size <= 6)  // 3*2
    }

    @Test
    fun `formats exchanges correctly`() {
        val exchanges = listOf(
            ContextManager.ConversationExchange("user", "Hello"),
            ContextManager.ConversationExchange("assistant", "Hi")
        )

        val formatted = contextManager.formatExchangesForModel(exchanges)
        assertTrue("Should contain user message", formatted.contains("user: Hello"))
        assertTrue("Should contain assistant message", formatted.contains("assistant: Hi"))
    }

    @Test
    fun `builds full prompt correctly`() {
        val exchanges = listOf(
            ContextManager.ConversationExchange("user", "Hello")
        )

        val prompt = contextManager.buildFullPrompt(
            systemPrompt = "You are helpful.",
            exchanges = exchanges
        )

        assertTrue("Should contain system prompt", prompt.contains("You are helpful."))
        assertTrue("Should contain exchange", prompt.contains("user: Hello"))
    }

    @Test
    fun `handles empty exchanges`() {
        val model = createTestModel()

        val result = contextManager.manageContext(
            exchanges = emptyList(),
            systemPrompt = "System",
            modelDescriptor = model
        )

        assertFalse("Should not truncate with no exchanges", result.wasTruncated)
        assertTrue("Should have no exchanges", result.exchanges.isEmpty())
    }

    @Test
    fun `respects custom window size`() {
        val model = createTestModel(maxContext = 100)
        val exchanges = (1..30).map {
            ContextManager.ConversationExchange("user", "Message $it")
        }

        val result = contextManager.manageContext(
            exchanges = exchanges,
            systemPrompt = "System",
            modelDescriptor = model,
            windowSize = 10
        )

        assertTrue("Should truncate", result.wasTruncated)
        assertEquals("Should keep 20 exchanges (10 pairs)", 20, result.exchanges.size)
    }

    @Test
    fun `respects custom threshold`() {
        val model = createTestModel(maxContext = 1000)
        val exchanges = (1..50).map {
            ContextManager.ConversationExchange("user", "Long message content here $it")
        }

        val result = contextManager.manageContext(
            exchanges = exchanges,
            systemPrompt = "This is a very long system prompt to test the threshold functionality",
            modelDescriptor = model,
            threshold = 0.5f  // 50% threshold - more aggressive
        )

        // With lower threshold, should truncate more aggressively
        assertTrue("Should truncate with lower threshold", result.wasTruncated)
    }
}