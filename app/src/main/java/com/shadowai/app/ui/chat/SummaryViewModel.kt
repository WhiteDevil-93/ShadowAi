package com.shadowai.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.ai.ConversationSummarizer
import com.shadowai.app.storage.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing conversation summaries.
 *
 * Handles automatic summarization triggers, manual summarization requests,
 * and UI state for displaying summaries.
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summarizer: ConversationSummarizer,
    private val conversationRepository: ConversationRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: android.content.Context
) : ViewModel() {

    /**
     * UI state for summarization.
     */
    data class UiState(
        val canSummarize: Boolean = false,
        val contextUsage: Float = 0f,
        val threshold: Float = 0.7f,
        val recommendation: String? = null,
        val isSummarizing: Boolean = false,
        val hasUnviewedSummaries: Boolean = false,
        val summaryCount: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Currently viewed summary (for detail view).
     * Uses in-memory state flow to avoid storing non-Parcelable objects in SavedStateHandle.
     */
    private val _currentSummary = MutableStateFlow<ConversationSummarizer.StoredSummary?>(null)
    val currentSummary: StateFlow<ConversationSummarizer.StoredSummary?> = _currentSummary.asStateFlow()

    /**
     * All summaries for current conversation.
     * Uses in-memory state flow to avoid storing non-Parcelable objects in SavedStateHandle.
     */
    private val _summaries = MutableStateFlow<List<ConversationSummarizer.StoredSummary>>(emptyList())
    val summaries: StateFlow<List<ConversationSummarizer.StoredSummary>> = _summaries.asStateFlow()

    init {
        viewModelScope.launch {
            _summaries.collect { items ->
                _uiState.update { it.copy(summaryCount = items.size) }
            }
        }
    }

    /**
     * Update summarization configuration.
     */
    fun updateConfig(
        enabled: Boolean = true,
        threshold: Float = 0.7f,
        targetTokens: Int = 512,
        minMessages: Int = 5
    ) {
        summarizer.updateConfig(
            ConversationSummarizer.Config(
                enabled = enabled,
                threshold = threshold,
                targetTokens = targetTokens,
                minMessagesToSummarize = minMessages
            )
        )
    }

    /**
     * Check if conversation should be summarized and update UI state.
     */
    fun checkSummarizationNeeded(messages: List<ChatMessage>, maxContext: Int) {
        viewModelScope.launch {
            val result = summarizer.shouldSummarize(messages, maxContext)

            _uiState.update { state ->
                state.copy(
                    canSummarize = result.shouldSummarize,
                    contextUsage = result.currentUsage,
                    threshold = result.threshold,
                    recommendation = result.recommendation
                )
            }
        }
    }

    /**
     * Trigger automatic summarization.
     */
    fun summarizeAutomatically(messages: List<ChatMessage>, maxContext: Int, conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true) }

            summarizer.summarizeConversation(messages, maxContext)
                .onSuccess { summary ->
                    // Save summary to repository
                    conversationRepository.saveSummary(conversationId, summary, summary.summarizedMessages)

                    // Update summaries list
                    val currentSummaries = _summaries.value
                    _summaries.value = currentSummaries + summary

                    // Update UI state
                    updateHasUnviewedSummaries(true)
                }
                .onFailure { error ->
                    // Log error but don't crash
                    android.util.Log.e("SummaryViewModel", "Auto-summarization failed", error)
                }

            _uiState.update { it.copy(isSummarizing = false) }
        }
    }

    /**
     * Trigger manual summarization (user-initiated).
     */
    fun summarizeManually(messages: List<ChatMessage>, maxContext: Int, conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true) }

            summarizer.summarizeConversation(messages, maxContext)
                .onSuccess { summary ->
                    conversationRepository.saveSummary(conversationId, summary, summary.summarizedMessages)
                    val currentSummaries = _summaries.value
                    _summaries.value = currentSummaries + summary
                    _currentSummary.value = summary
                }
                .onFailure { error ->
                    // Handle error - could show toast or dialog
                    android.util.Log.e("SummaryViewModel", "Manual summarization failed", error)
                }

            _uiState.update { it.copy(isSummarizing = false) }
        }
    }

    /**
     * View a specific summary.
     */
    fun viewSummary(summary: ConversationSummarizer.StoredSummary) {
        _currentSummary.value = summary
        updateHasUnviewedSummaries(false)
    }

    /**
     * Clear current summary view.
     */
    fun clearSummaryView() {
        _currentSummary.value = null
    }

    /**
     * Copy summary content to clipboard.
     */
    fun copySummary(content: String, onSuccess: () -> Unit) {
        try {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            if (clipboard != null) {
                val clip = android.content.ClipData.newPlainText("Summary", content)
                clipboard.setPrimaryClip(clip)
                onSuccess()
            } else {
                android.util.Log.w("SummaryViewModel", "Clipboard service unavailable")
            }
        } catch (e: Exception) {
            android.util.Log.e("SummaryViewModel", "Failed to copy summary", e)
        }
    }

    /**
     * Restore conversation from summary (expand summarized section).
     */
    fun restoreFromSummary(
        summary: ConversationSummarizer.StoredSummary,
        onRestored: (List<ChatMessage>) -> Unit,
        conversationId: String
    ) {
        viewModelScope.launch {
            val restoredMessages = summary.summarizedMessages
            onRestored(restoredMessages)

            // Remove from repository and list
            try {
                conversationRepository.deleteSummary(conversationId, summary.metadata.id)
            } catch (e: Exception) {
                android.util.Log.e("SummaryViewModel", "Failed to delete summary from repository", e)
            }

            val currentSummaries = _summaries.value
            _summaries.value = currentSummaries.filterNot { summaries ->
                summaries.metadata.id == summary.metadata.id
            }
        }
    }

    /**
     * Dismiss a summary without restoring.
     */
    fun dismissSummary(summary: ConversationSummarizer.StoredSummary, conversationId: String) {
        viewModelScope.launch {
            // Remove from repository silently
            try {
                conversationRepository.deleteSummary(conversationId, summary.metadata.id)
            } catch (e: Exception) {
                android.util.Log.e("SummaryViewModel", "Failed to delete summary from repository", e)
            }

            val currentSummaries = _summaries.value
            _summaries.value = currentSummaries.filterNot { summaries ->
                summaries.metadata.id == summary.metadata.id
            }

            if (_currentSummary.value?.metadata?.id == summary.metadata.id) {
                _currentSummary.value = null
            }
        }
    }

    /**
     * Get all summaries for a conversation.
     */
    fun loadSummaries(conversationId: String) {
        viewModelScope.launch {
            try {
                val summaries = conversationRepository.getSummariesSync(conversationId)
                _summaries.value = summaries
            } catch (e: Exception) {
                android.util.Log.e("SummaryViewModel", "Failed to load summaries", e)
                _summaries.value = emptyList()
            }
        }
    }

    /**
     * Mark summaries as viewed.
     */
    fun markAllAsViewed() {
        updateHasUnviewedSummaries(false)
    }

    /**
     * Delete all summaries for a conversation.
     */
    fun deleteAllSummaries(conversationId: String) {
        viewModelScope.launch {
            try {
                conversationRepository.deleteAllSummaries(conversationId)
            } catch (e: Exception) {
                android.util.Log.e("SummaryViewModel", "Failed to delete all summaries", e)
            }

            _summaries.value = emptyList()
            _currentSummary.value = null
        }
    }

    /**
     * Update hasUnviewedSummaries flag.
     */
    private fun updateHasUnviewedSummaries(value: Boolean) {
        _uiState.update { it.copy(hasUnviewedSummaries = value, summaryCount = _summaries.value.size) }
    }

    /**
     * Get estimated token count for text.
     */
    fun estimateTokens(text: String): Int {
        return summarizer.estimateTokenCount(text)
    }

    /**
     * Get estimated context usage for messages.
     */
    fun getContextUsage(messages: List<ChatMessage>, maxContext: Int): Float {
        return summarizer.estimateContextTokens(messages).toFloat() / maxContext
    }
}
