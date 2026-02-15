package com.shadowai.app.ui.history

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.export.ConversationExporter
import com.shadowai.app.export.ConversationExporter.ExportFormat
import com.shadowai.app.ui.ChatMessage
import com.shadowai.app.db.MessageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for Chat History screen.
 * Manages loading, displaying, and exporting conversation history.
 */
@HiltViewModel
class ChatHistoryViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationExporter: ConversationExporter
) : ViewModel() {

    companion object {
        private const val TAG = "ChatHistoryViewModel"
    }

    private val _uiState = MutableStateFlow(ChatHistoryUiState())
    val uiState: StateFlow<ChatHistoryUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    init {
        loadConversations()
    }

    /**
     * Load all conversations from the database.
     * Groups messages by conversation/thread.
     */
    private fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Get all messages and group by conversation
                val allMessages = messageDao.getAllMessages()
                val conversations = groupMessagesByConversation(allMessages)

                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversations", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportStatus = ExportStatus.Error("Failed to load conversations: ${e.message}")
                )
            }
        }
    }

    /**
     * Group messages into conversation summaries.
     * In a real implementation, this would use conversation IDs from the database.
     */
    private fun groupMessagesByConversation(messages: List<com.shadowai.app.db.ChatMessageEntity>): List<ConversationSummary> {
        // For now, create a single conversation with all messages
        // In a full implementation, you'd have proper conversation threading
        return if (messages.isNotEmpty()) {
            listOf(
                ConversationSummary(
                    id = "default",
                    title = messages.firstOrNull()?.text?.take(50) ?: "Conversation",
                    messageCount = messages.size,
                    lastUpdated = dateFormat.format(Date(messages.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()))
                )
            )
        } else {
            emptyList()
        }
    }

    /**
     * Delete a specific conversation.
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                // In a full implementation, delete by conversation ID
                messageDao.clearHistory()
                dismissDialogs()
                loadConversations() // Refresh the list
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation", e)
                _uiState.value = _uiState.value.copy(
                    exportStatus = ExportStatus.Error("Failed to delete: ${e.message}")
                )
            }
        }
    }

    /**
     * Clear all conversation history.
     */
    fun clearAllConversations() {
        viewModelScope.launch {
            try {
                messageDao.clearHistory()
                dismissDialogs()
                loadConversations()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear conversations", e)
                _uiState.value = _uiState.value.copy(
                    exportStatus = ExportStatus.Error("Failed to clear history: ${e.message}")
                )
            }
        }
    }

    /**
     * Export a specific conversation.
     */
    fun exportConversation(context: Context, conversationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exportStatus = ExportStatus.InProgress)
            try {
                val messages = messageDao.getAllMessages().map { entity ->
                    ChatMessage(
                        id = entity.id,
                        text = entity.text,
                        isUser = entity.isUser,
                        timestamp = entity.timestamp,
                        modelName = entity.modelName,
                        error = entity.error
                    )
                }

                val result = conversationExporter.exportConversation(messages, ExportFormat.MARKDOWN)
                if (result.success && result.uri != null) {
                    conversationExporter.shareExportedFile(java.io.File(result.uri.path!!))
                    _uiState.value = _uiState.value.copy(
                        exportStatus = ExportStatus.Success(result.uri.toString())
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        exportStatus = ExportStatus.Error(result.error ?: "Export failed")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.value = _uiState.value.copy(
                    exportStatus = ExportStatus.Error("Export failed: ${e.message}")
                )
            }
        }
    }

    /**
     * Toggle conversation selection for batch export
     */
    fun toggleConversationSelection(conversationId: String) {
        val currentState = _uiState.value
        val selected = currentState.selectedConversations
        
        val newSelected = if (selected.contains(conversationId)) {
            selected - conversationId
        } else {
            selected + conversationId
        }
        
        _uiState.value = currentState.copy(
            selectedConversations = newSelected,
            isInSelectionMode = newSelected.isNotEmpty()
        )
    }

    /**
     * Select all conversations for batch export
     */
    fun selectAllConversations() {
        val allIds = _uiState.value.conversations.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedConversations = allIds,
            isInSelectionMode = true
        )
    }

    /**
     * Clear all conversation selections
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedConversations = emptySet(),
            isInSelectionMode = false
        )
    }

    /**
     * Show batch export dialog
     */
    fun showBatchExport() {
        _uiState.value = _uiState.value.copy(
            showBatchExportDialog = true,
            batchExportResult = null
        )
    }

    /**
     * Hide batch export dialog
     */
    fun hideBatchExport() {
        _uiState.value = _uiState.value.copy(
            showBatchExportDialog = false,
            batchExportResult = null
        )
    }

    /**
     * Export selected conversations
     */
    fun exportSelectedConversations(context: Context, format: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBatchExporting = true,
                batchExportResult = null
            )
            
            try {
                // Get selected conversations or all if none selected
                val selectedIds = _uiState.value.selectedConversations
                val conversationsToExport = if (selectedIds.isEmpty()) {
                    _uiState.value.conversations
                } else {
                    _uiState.value.conversations.filter { selectedIds.contains(it.id) }
                }
                
                if (conversationsToExport.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isBatchExporting = false,
                        batchExportResult = BatchExportResult.Error("No conversations selected")
                    )
                    return@launch
                }
                
                // For now, just export the single conversation we have
                val messages = messageDao.getAllMessages().map { entity ->
                    ChatMessage(
                        id = entity.id,
                        text = entity.text,
                        isUser = entity.isUser,
                        timestamp = entity.timestamp,
                        modelName = entity.modelName,
                        error = entity.error
                    )
                }
                
                val exportFormat = when (format.uppercase()) {
                    "PDF" -> ExportFormat.PDF
                    "MARKDOWN" -> ExportFormat.MARKDOWN
                    "JSON" -> ExportFormat.JSON
                    else -> ExportFormat.MARKDOWN // Default to Markdown for ZIP or unknown formats
                }
                
                val result = conversationExporter.exportConversation(
                    messages,
                    exportFormat,
                    filename = "shadowai_export_${System.currentTimeMillis()}"
                )
                
                if (result.success && result.uri != null) {
                    conversationExporter.shareExportedFile(java.io.File(result.uri.path!!))
                    _uiState.value = _uiState.value.copy(
                        isBatchExporting = false,
                        batchExportResult = BatchExportResult.Success(result.uri.toString())
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isBatchExporting = false,
                        batchExportResult = BatchExportResult.Error(result.error ?: "Export failed")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batch export failed", e)
                _uiState.value = _uiState.value.copy(
                    isBatchExporting = false,
                    batchExportResult = BatchExportResult.Error("Export failed: ${e.message}")
                )
            }
        }
    }

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteConfirmation(conversationId: String) {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = conversationId)
    }

    /**
     * Show clear all confirmation dialog.
     */
    fun showClearAllConfirmation() {
        _uiState.value = _uiState.value.copy(showClearAllConfirmation = true)
    }

    /**
     * Dismiss all dialogs.
     */
    fun dismissDialogs() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = null,
            showClearAllConfirmation = false,
            showBatchExportDialog = false
        )
    }
}
