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
