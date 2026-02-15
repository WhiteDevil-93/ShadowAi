package com.shadowai.app.ai

import android.util.Log
import com.shadowai.app.ui.chat.ChatMessage
import com.shadowai.app.ui.chat.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

/**
 * Manages conversation summarization to extend effective context window.
 *
 * Automatically summarizes conversations when context >70% full.
 * Summaries are stored separately and can be viewed/copied by users.
 *
 * Uses ILlamaEngine interface to avoid circular dependencies in DI graph.
 *
 * M-10 DOCUMENTED: AI fallback behavior documented in [createSimpleSummary] method.
 * When LLM inference is unavailable, falls back to structured text-based summarization.
 */
@javax.inject.Singleton
class ConversationSummarizer @javax.inject.Inject constructor(
    private val modelManager: ILlamaEngine,
    // M-12 FIXED: Consolidated token counting - uses injected TokenCounter
    // Removed duplicate token counting implementation
    private val tokenCounter: TokenCounter
) {

    companion object {
        private const val TAG = "ConversationSummarizer"
        private const val DEFAULT_SUMMARY_THRESHOLD = 0.7f // 70%
        private const val SUMMARY_TARGET_TOKENS = 512 // Keep summaries under this length
    }

    /**
     * Metadata for a summarized conversation section.
     */
    @Serializable
    data class SummaryMetadata(
        val id: String,
        val createdAt: Long,
        val messageCount: Int,
        val originalContextTokens: Int,
        val summaryTokens: Int,
        val timestampBegin: Long,
        val timestampEnd: Long
    )

    /**
     * Stored summary with metadata and content.
     */
    data class StoredSummary(
        val metadata: SummaryMetadata,
        val content: String,
        val summarizedMessages: List<ChatMessage>
    ) {
        fun toJson(): String {
            val payload = buildJsonObject {
                // FIX: Explicitly specify serializer for metadata
                put("metadata", Json.encodeToJsonElement(SummaryMetadata.serializer(), metadata))
                put("content", JsonPrimitive(content))
                put(
                    "summarizedMessageIds",
                    JsonArray(summarizedMessages.map { JsonPrimitive(it.id) })
                )
            }
            return payload.toString()
        }
    }

    /**
     * Result from a summarization check.
     */
    data class SummarizationResult(
        val shouldSummarize: Boolean,
        val currentUsage: Float,
        val threshold: Float = DEFAULT_SUMMARY_THRESHOLD,
        val recommendation: String? = null
    )

    /**
     * Configuration for summarization behavior.
     */
    data class Config(
        val enabled: Boolean = true,
        val threshold: Float = DEFAULT_SUMMARY_THRESHOLD,
        val targetTokens: Int = SUMMARY_TARGET_TOKENS,
        val minMessagesToSummarize: Int = 5
    )

    private var config = Config()

    /**
     * Update summarization configuration.
     */
    fun updateConfig(newConfig: Config) {
        config = newConfig
        Log.d(TAG, "Summary config updated: enabled=${config.enabled}, threshold=${config.threshold}")
    }

    /**
     * Check if conversation should be summarized.
     *
     * @param messages Current conversation messages
     * @param maxContext Maximum context window size
     * @return SummarizationResult with recommendation
     */
    fun shouldSummarize(messages: List<ChatMessage>, maxContext: Int): SummarizationResult {
        if (!config.enabled) {
            return SummarizationResult(
                shouldSummarize = false,
                currentUsage = 0f,
                recommendation = "Auto-summarization is disabled"
            )
        }

        if (messages.size < config.minMessagesToSummarize) {
            return SummarizationResult(
                shouldSummarize = false,
                currentUsage = 0f,
                recommendation = "Not enough messages to summarize (minimum ${config.minMessagesToSummarize})"
            )
        }

        // Estimate current context usage
        val estimatedTokens = estimateContextTokens(messages)
        val usageRatio = estimatedTokens.toFloat() / maxContext

        val shouldSummarize = usageRatio >= config.threshold
        val recommendation = when {
            !shouldSummarize -> "Context at ${String.format("%.1f%%", usageRatio * 100)} (below ${String.format("%.0f%%", config.threshold * 100)} threshold)"
            shouldSummarize -> "Context at ${String.format("%.1f%%", usageRatio * 100)} (exceeds threshold). Consider summarizing to free up space."
            else -> null
        }

        return SummarizationResult(
            shouldSummarize = shouldSummarize,
            currentUsage = usageRatio,
            threshold = config.threshold,
            recommendation = recommendation
        )
    }

    /**
     * Summarize the beginning of a conversation.
     *
     * @param messages Full conversation message list
     * @param maxContext Maximum context window size
     * @return Pair of (summary, remaining messages)
     */
    suspend fun summarizeConversation(
        messages: List<ChatMessage>,
        maxContext: Int
    ): Result<StoredSummary> = withContext(Dispatchers.IO) {
        try {
            if (messages.isEmpty()) {
                return@withContext Result.failure(Exception("No messages to summarize"))
            }

            Log.d(TAG, "Starting summarization for ${messages.size} messages...")

            // Determine how many messages to summarize
            val (toSummarize, _) = determineMessagesToSummarize(messages, maxContext)

            if (toSummarize.isEmpty()) {
                return@withContext Result.failure(Exception("No messages qualify for summarization"))
            }

            // Generate summary using the model
            val content = generateSummary(toSummarize).getOrElse { error ->
                return@withContext Result.failure(error)
            }

            // Create summary metadata
            val metadata = SummaryMetadata(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                messageCount = toSummarize.size,
                originalContextTokens = estimateContextTokens(toSummarize),
                summaryTokens = estimateTokenCount(content),
                timestampBegin = toSummarize.firstOrNull()?.timestamp
                    ?: System.currentTimeMillis(),
                timestampEnd = toSummarize.lastOrNull()?.timestamp
                    ?: System.currentTimeMillis()
            )

            val summary = StoredSummary(
                metadata = metadata,
                content = content,
                summarizedMessages = toSummarize
            )

            Log.i(TAG, "Summary created: ${metadata.messageCount} messages -> ${content.length} chars")
            Result.success(summary)

        } catch (e: IllegalStateException) {
            Log.e(TAG, "Conversation in invalid state for summarization", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during summarization", e)
            Result.failure(e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Summarization cancelled")
            throw e
        }
    }

    /**
     * Determine which messages should be summarized vs kept.
     *
     * We summarize the oldest messages while keeping recent context.
     */
    private fun determineMessagesToSummarize(
        messages: List<ChatMessage>,
        maxContext: Int
    ): Pair<List<ChatMessage>, List<ChatMessage>> {
        // Estimate tokens needed for remaining after summarization
        val targetRemainingTokens = (maxContext * (1 - config.threshold * 0.8)).toInt()
        // Target: keep 80% of threshold after summarization

        var estimatedTokens = 0
        var splitIndex = messages.size

        // Work backwards from newest to find where to split
        for (i in messages.size - 1 downTo 0) {
            val msgTokens = estimateMessageTokens(messages[i])
            if (estimatedTokens + msgTokens > targetRemainingTokens) {
                splitIndex = i + 1
                break
            }
            estimatedTokens += msgTokens
        }

        val toSummarize = if (splitIndex >= config.minMessagesToSummarize) {
            messages.take(splitIndex)
        } else {
            emptyList()
        }

        val toKeep = messages.drop(splitIndex)

        return Pair(toSummarize, toKeep)
    }

    /**
     * Generate a summary using the Llama model with AI fallback behavior.
     * M-10: Enhanced with proper AI summarization when available.
     */
    private suspend fun generateSummary(messages: List<ChatMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // Build a prompt for summarization
                val prompt = buildSummaryPrompt(messages)

                // M-10: First try to generate summary using the loaded AI model
                val aiSummary = if (modelManager.isLoaded()) {
                    try {
                        // Attempt to generate using the LLM
                        val handle = modelManager.loadModel("") // Empty path should return loaded model
                        if (handle != null) {
                            modelManager.generate(handle, prompt)
                        } else {
                            ""
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AI summarization failed, using fallback: ${e.message}")
                        ""
                    }
                } else {
                    ""
                }

                // M-10: Use AI summary if successful, otherwise fallback to structured text summary
                val summary = if (aiSummary.isNotBlank()) {
                    aiSummary
                } else {
                    createSimpleSummary(messages)
                }

                Result.success(summary)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during summary generation", e)
                Result.failure(e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Invalid state during summary generation", e)
                Result.failure(e)
            }
        }

    /**
     * Build a prompt for the model to summarize.
     */
    private fun buildSummaryPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()

        sb.append("Summarize the following conversation concisely. ")
        sb.append("Focus on key topics, decisions, and important information. ")
        sb.append("Keep under ${config.targetTokens} words.\n\n")

        messages.forEach { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "User"
                ChatRole.ASSISTANT -> "Assistant"
                ChatRole.SYSTEM -> "System"
            }
            sb.append("$role: ${msg.content}\n")
        }

        sb.append("\nSummary:")

        return sb.toString()
    }

    /**
     * Create a simple text-based summary when model is unavailable.
     *
     * FALLBACK BEHAVIOR:
     * This method serves as a fallback when the LLM-based summarization fails or
     * is unavailable. Instead of relying on the model's inference capabilities,
     * it generates a structured text summary containing:
     * - Message counts by role (user/assistant/system)
     * - Truncated preview of user messages (first 500 chars)
     * - Timestamp information
     *
     * USE CASES:
     * - Model not loaded or unavailable
     * - Generate summary for brief content inspection
     * - Quick export without model inference overhead
     *
     * @param messages List of messages to summarize
     * @return A formatted plain-text summary
     */
    private fun createSimpleSummary(messages: List<ChatMessage>): String {
        val sb = StringBuilder()

        // Count messages by role
        val userMsgs = messages.count { it.role == ChatRole.USER }
        val assistantMsgs = messages.count { it.role == ChatRole.ASSISTANT }
        val systemMsgs = messages.count { it.role == ChatRole.SYSTEM }

        // Extract key topics from user messages
        val userContent = messages
            .filter { it.role == ChatRole.USER }
            .joinToString(" ... ") { it.content.take(100) }

        // Build summary
        sb.append("Conversation Summary\n")
        sb.append("=" .repeat(40) + "\n\n")
        sb.append("This conversation spanned ${messages.size} messages:\n")
        sb.append("• $userMsgs user messages\n")
        sb.append("• $assistantMsgs assistant responses\n")
        if (systemMsgs > 0) sb.append("• $systemMsgs system messages\n")
        sb.append("\nMain topics discussed:\n")
        sb.append(userContent.take(500))
        sb.append("\n")
        sb.append("... (${messages.size} messages summarized)\n")

        return sb.toString()
    }

    /**
     * Estimate total context tokens for a message list.
     * M-12: Uses centralized TokenCounter - no duplicate implementation.
     */
    fun estimateContextTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { estimateMessageTokens(it) }
    }

    /**
     * Estimate tokens for a single message.
     * M-12: Delegates to centralized TokenCounter.
     */
    private fun estimateMessageTokens(message: ChatMessage): Int {
        return tokenCounter.countTokens(message.content)
    }

    /**
     * Estimate token count from text.
     * M-12: Delegates to centralized TokenCounter.
     */
    fun estimateTokenCount(text: String): Int {
        return tokenCounter.countTokens(text)
    }

    /**
     * Check if a summary has been applied to a message list.
     */
    fun hasSummary(messages: List<ChatMessage>): Boolean {
        return messages.any { it.metadata?.get("summary_id") != null }
    }

    /**
     * Get summary ID from message metadata.
     */
    fun getSummaryId(message: ChatMessage): String? {
        return message.metadata?.get("summary_id")
    }

    /**
     * Create a placeholder message indicating a summarized section.
     */
    fun createSummaryPlaceholder(summary: StoredSummary): ChatMessage {
        val timestamp = summary.metadata.timestampBegin

        val summaryMessage = ChatMessage(
            id = "summary-${summary.metadata.id}",
            role = ChatRole.SYSTEM,
            content = buildPlaceholderContent(summary),
            timestamp = timestamp,
            metadata = mapOf(
                "summary_id" to summary.metadata.id,
                "is_summary" to "true",
                "message_count" to summary.metadata.messageCount.toString()
            )
        )

        return summaryMessage
    }

    /**
     * Build user-friendly placeholder content.
     */
    private fun buildPlaceholderContent(summary: StoredSummary): String {
        return buildString {
            append("\n⬇️ ")
            append("${summary.metadata.messageCount} earlier messages summarized\n")
            append("Tap to view summary\n")
        }
    }
}
