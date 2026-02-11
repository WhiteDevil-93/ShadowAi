#!/bin/bash
# Agent 2: ContextManager Implementation
echo "🤖 Agent 2: ContextManager Implementation starting..."

cd /mnt/c/Users/anon3/Downloads/ShadowAi

# Create ContextManager.kt
cat > app/src/main/java/com/shadowai/app/agent/ContextManager.kt << 'KOTLIN_EOF'
package com.shadowai.app.agent

import com.shadowai.app.ai.TokenCounter
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages conversation context with sliding window truncation.
 * Ensures context stays within model's token budget.
 */
@Singleton
class ContextManager @Inject constructor(
    private val tokenCounter: TokenCounter
) {

    companion object {
        /**
         * Minimum tokens needed for generation (conservative estimate).
         */
        private const val MIN_GENERATION_TOKENS = 256

        /**
         * Default sliding window size (number of exchanges to keep).
         */
        private const val DEFAULT_SLIDING_WINDOW_SIZE = 5

        /**
         * Default context threshold (percentage of maxContext before truncation).
         */
        private const val DEFAULT_CONTEXT_THRESHOLD = 0.9f
    }

    data class ConversationExchange(
        val role: String,  // "user", "assistant", "system"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ContextTruncationResult(
        val exchanges: List<ConversationExchange>,
        val systemPrompt: String,
        val wasTruncated: Boolean,
        val originalTokens: Int,
        val truncatedTokens: Int
    )

    /**
     * Manages conversation context with sliding window.
     *
     * @param exchanges List of conversation exchanges (user/assistant)
     * @param systemPrompt System prompt to preserve
     * @param modelDescriptor Model context limits
     * @param windowSize Number of recent exchanges to keep (default: 5)
     * @param threshold Context usage threshold (default: 0.9 = 90%)
     */
    fun manageContext(
        exchanges: List<ConversationExchange>,
        systemPrompt: String,
        modelDescriptor: ModelDescriptor,
        windowSize: Int = DEFAULT_SLIDING_WINDOW_SIZE,
        threshold: Float = DEFAULT_CONTEXT_THRESHOLD
    ): ContextTruncationResult {
        val systemPromptTokens = tokenCounter.countTokens(systemPrompt)
        val exchangeTokens = tokenCounter.countTokens(exchanges.map { it.content })
        val totalTokens = systemPromptTokens + exchangeTokens

        val maxTokens = (modelDescriptor.maxContext * threshold).toInt()
        val requiredForGeneration = MIN_GENERATION_TOKENS
        val availableForExchanges = maxTokens - systemPromptTokens - requiredForGeneration

        // If context fits, no truncation needed
        if (exchangeTokens <= availableForExchanges) {
            return ContextTruncationResult(
                exchanges = exchanges,
                systemPrompt = systemPrompt,
                wasTruncated = false,
                originalTokens = totalTokens,
                truncatedTokens = totalTokens
            )
        }

        // Apply sliding window: keep last N exchanges
        val truncatedExchanges = exchanges.takeLast(windowSize * 2)  // *2 for user/assistant pairs
        val truncatedTokens = systemPromptTokens + tokenCounter.countTokens(truncatedExchanges.map { it.content })

        return ContextTruncationResult(
            exchanges = truncatedExchanges,
            systemPrompt = systemPrompt,
            wasTruncated = true,
            originalTokens = totalTokens,
            truncatedTokens = truncatedTokens
        )
    }

    /**
     * Formats conversation exchanges for model input.
     */
    fun formatExchangesForModel(exchanges: List<ConversationExchange>): String {
        return exchanges.joinToString("\n") { exchange ->
            "${exchange.role}: ${exchange.content}"
        }
    }

    /**
     * Reconstructs full context prompt (system + exchanges).
     */
    fun buildFullPrompt(
        systemPrompt: String,
        exchanges: List<ConversationExchange>
    ): String {
        val formattedExchanges = formatExchangesForModel(exchanges)
        return if (formattedExchanges.isNotBlank()) {
            "$systemPrompt\n\n$formattedExchanges"
        } else {
            systemPrompt
        }
    }
}
KOTLIN_EOF

echo "✅ ContextManager.kt created"

# Create test file
cat > app/src/test/kotlin/com/shadowai/app/agent/ContextManagerTest.kt << 'TEST_EOF'
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
TEST_EOF

echo "✅ ContextManagerTest.kt created"
echo "✅ Agent 2: ContextManager Implementation complete!"