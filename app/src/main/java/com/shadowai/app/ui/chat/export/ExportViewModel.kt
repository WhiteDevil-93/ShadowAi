package com.shadowai.app.ui.chat.export

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.db.ChatMessageEntity
import com.shadowai.app.db.MessageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for export operations
 */
data class ExportUiState(
    val isExporting: Boolean = false,
    val exportResult: ExportResult? = null,
    val showExportDialog: Boolean = false,
    val availableConversations: List<Triple<String, String, List<ChatMessageEntity>>> = emptyList(),
    val selectedConversations: Set<String> = emptySet(),
    val isBatchMode: Boolean = false
)

/**
 * ViewModel for handling conversation export operations.
 * Supports single conversation export and batch export.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    application: Application,
    private val messageDao: MessageDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val exportManager = ExportManager(application.applicationContext)

    /**
     * Show the export dialog for single conversation export
     */
    fun showExportDialog() {
        _uiState.value = _uiState.value.copy(
            showExportDialog = true,
            exportResult = null,
            isBatchMode = false
        )
    }

    /**
     * Show the export dialog for batch export (multiple conversations)
     */
    fun showBatchExportDialog() {
        _uiState.value = _uiState.value.copy(
            showExportDialog = true,
            exportResult = null,
            isBatchMode = true
        )
        loadConversations()
    }

    /**
     * Hide the export dialog
     */
    fun hideExportDialog() {
        _uiState.value = _uiState.value.copy(
            showExportDialog = false,
            isBatchMode = false,
            selectedConversations = emptySet()
        )
    }

    /**
     * Export the current conversation in the specified format
     */
    fun exportConversation(
        conversationTitle: String,
        format: ExportFormat,
        shareAfter: Boolean = true,
        conversationId: String? = null,
        modelUsed: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportResult = null
            )

            try {
                val messages = messageDao.getAllMessages()

                val result = exportManager.exportConversation(
                    title = conversationTitle,
                    messages = messages,
                    format = format,
                    conversationId = conversationId,
                    modelUsed = modelUsed
                )

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = result
                )

                if (shareAfter && result is ExportResult.Success) {
                    exportManager.shareExport(result.uri, format)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = ExportResult.Error("Export failed: ${e.message}")
                )
            }
        }
    }

    /**
     * Export multiple conversations as a batch (ZIP)
     */
    fun exportConversationsBatch(
        format: ExportFormat,
        shareAfter: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportResult = null
            )

            try {
                val selectedIds = _uiState.value.selectedConversations
                val conversations = if (selectedIds.isEmpty()) {
                    _uiState.value.availableConversations
                } else {
                    _uiState.value.availableConversations.filter {
                        selectedIds.contains(it.first) // first is the ID
                    }
                }

                if (conversations.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportResult = ExportResult.Error("No conversations to export")
                    )
                    return@launch
                }

                val result = exportManager.exportConversationsBatch(conversations, format)

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = result
                )

                if (shareAfter && result is ExportResult.Success) {
                    // FIX: Use ExportFormat.ZIP for sharing batch export
                    exportManager.shareExport(result.uri, ExportFormat.ZIP)
                }
            } catch (e: Exception) {
                Log.e("ExportViewModel", "Batch export failed", e)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = ExportResult.Error("Batch export failed: ${e.message}")
                )
            }
        }
    }

    /**
     * Toggle conversation selection for batch export
     */
    fun toggleConversationSelection(conversationId: String) {
        val currentSelected = _uiState.value.selectedConversations
        val newSelected = if (currentSelected.contains(conversationId)) {
            currentSelected - conversationId
        } else {
            currentSelected + conversationId
        }
        _uiState.value = _uiState.value.copy(selectedConversations = newSelected)
    }

    /**
     * Select all conversations for batch export
     */
    fun selectAllConversations() {
        val allIds = _uiState.value.availableConversations.map { it.first }.toSet()
        _uiState.value = _uiState.value.copy(selectedConversations = allIds)
    }

    /**
     * Clear all conversation selections
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedConversations = emptySet())
    }

    /**
     * Share an already exported file
     * Automatically detects ZIP files for batch exports
     */
    fun shareExport(result: ExportResult.Success) {
        // Check if it's a batch export (ZIP file)
        if (result.fileName.endsWith(".zip", ignoreCase = true)) {
            // Fix: Use ExportFormat.ZIP instead of string literal
            exportManager.shareExport(result.uri, ExportFormat.ZIP)
        } else {
            exportManager.shareExport(result.uri, result.format)
        }
    }

    /**
     * Clear any previous export result
     */
    fun clearResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    /**
     * Load available conversations for batch export
     */
    fun loadConversations() {
        viewModelScope.launch {
            val messages = messageDao.getAllMessages()

            // Group messages into conversations (for now, single conversation)
            val conversations = if (messages.isNotEmpty()) {
                listOf(
                    Triple(
                        "default-conversation",
                        messages.firstOrNull()?.text?.take(50) ?: "Current Conversation",
                        messages
                    )
                )
            } else {
                emptyList()
            }

            _uiState.value = _uiState.value.copy(
                availableConversations = conversations
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
