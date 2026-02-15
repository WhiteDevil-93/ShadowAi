package com.shadowai.app.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.agent.AgentResult
import com.shadowai.app.agent.ShadowAgent
import com.shadowai.app.db.MessageDao
import com.shadowai.app.db.toEntity
import com.shadowai.app.db.toModel
import com.shadowai.app.widget.ShadowAiWidgetProvider
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.core.security.PromptInjectionDefense
import com.shadowai.core.ProviderId
// REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.app.ui.workflows.ImageGenParams
import com.shadowai.core.Artifact
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents different types of errors that can occur in the chat.
 */
sealed class ChatError(val message: String) {
    class NetworkError(message: String) : ChatError(message)
    class AgentError(message: String) : ChatError(message)
    class DatabaseError(message: String) : ChatError(message)
    class UnknownError(message: String) : ChatError(message)

    companion object {
        fun fromException(e: Exception): ChatError {
            return when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.io.IOException -> NetworkError("Network error: ${e.message}")
                is android.database.SQLException -> DatabaseError("Database error: ${e.message}")
                else -> UnknownError(e.message ?: "Unknown error occurred")
            }
        }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: ChatError? = null,
    val sharedText: String? = null,  // Text from Intent Share Sheet
    val sharedImages: List<Uri> = emptyList()  // Images from Intent Share Sheet
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agent: ShadowAgent,
    private val messageDao: MessageDao,
    // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
    private val secretRepository: ProviderSecretRepository,
    private val piiMaskingProcessor: PiiMaskingProcessor,
    private val promptInjectionDefense: PromptInjectionDefense,
    // M-19: SavedStateHandle for navigation state persistence
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        
        // M-19: SavedStateHandle keys for navigation state persistence
        private const val KEY_IS_LOADING = "chat_is_loading"
        private const val KEY_SHARED_TEXT = "chat_shared_text"
        private const val KEY_SHARING_ACTIVE = "chat_sharing_active"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // M-19: Restore navigation state from SavedStateHandle
        restoreNavigationState()
        loadHistory()
    }

    // M-19: Save navigation state to SavedStateHandle
    private fun saveNavigationState() {
        savedStateHandle.set(KEY_IS_LOADING, _uiState.value.isLoading)
        savedStateHandle.set(KEY_SHARED_TEXT, _uiState.value.sharedText)
        savedStateHandle.set(KEY_SHARING_ACTIVE, _uiState.value.sharedImages.isNotEmpty())
    }

    // M-19: Restore navigation state from SavedStateHandle
    private fun restoreNavigationState() {
        val wasLoading = savedStateHandle.get<Boolean>(KEY_IS_LOADING) ?: false
        val sharedText = savedStateHandle.get<String>(KEY_SHARED_TEXT)
        val wasSharing = savedStateHandle.get<Boolean>(KEY_SHARING_ACTIVE) ?: false

        if (wasLoading || sharedText != null || wasSharing) {
            _uiState.value = _uiState.value.copy(
                isLoading = wasLoading,
                sharedText = sharedText
            )
            Log.d(
                TAG,
                "Restored navigation state: loading=$wasLoading, hasSharedText=${!sharedText.isNullOrBlank()}"
            )
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                // Load recent messages with pagination to avoid memory issues
                val totalCount = messageDao.getMessageCount()
                val limit = 100 // Load last 100 messages
                val offset = maxOf(0, totalCount - limit)
                val entities = messageDao.getMessages(limit, offset)
                val messages = entities.map { it.toModel() }
                _uiState.value = _uiState.value.copy(messages = messages, error = null)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load chat history", e)
                _uiState.value = _uiState.value.copy(
                    error = ChatError.DatabaseError("Failed to load history: ${e.message}")
                )
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // M-19: Save navigation state before loading
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            saveNavigationState()

            // Add user message immediately (with original text for local storage)
            val userMsg = ChatMessage(text = text, isUser = true)
            addMessage(userMsg)

            try {
                // Mask PII before sending to cloud providers
                val maskedText = if (piiMaskingProcessor.containsPii(text)) {
                    piiMaskingProcessor.maskPii(text)
                } else {
                    text
                }

                // Check for prompt injection attacks
                val verification = promptInjectionDefense.scan(maskedText)
                if (!verification.isSafe) {
                    _uiState.value = _uiState.value.copy(
                        error = ChatError.AgentError("Message blocked: ${verification.reason}"),
                        isLoading = false
                    )
                    return@launch
                }

                val result = agent.processInput(verification.sanitizedPrompt)
                when (result) {
                    is AgentResult.Conversation -> {
                        // Check if the response is actually an Artifact URI (simple heuristic for now)
                        val artifact = parseArtifactFromResponse(result.message)

                        addMessage(ChatMessage(
                            text = if (artifact != null) "" else result.message,
                            artifact = artifact,
                            isUser = false,
                            modelName = result.modelName,
                            executionSource = result.source,
                            plan = result.plan
                        ))
                    }
                    is AgentResult.ActionProposed -> {
                        addMessage(ChatMessage(
                            text = "Action proposed",
                            isUser = false,
                            modelName = result.modelName,
                            executionSource = result.source,
                            plan = result.plan,
                            proposedAction = result.action
                        ))
                    }
                    is AgentResult.Failure -> {
                        addMessage(ChatMessage(
                            text = "Error: ${result.error.message}",
                            isUser = false,
                            modelName = "System"
                        ))
                    }
                    is AgentResult.ConfirmationRequired -> {
                         addMessage(ChatMessage(
                            text = result.description,
                            isUser = false,
                            modelName = result.modelName,
                            executionSource = result.source,
                            proposedAction = result.action
                        ))
                    }
                    else -> {
                        addMessage(ChatMessage(
                            text = "Unknown response",
                            isUser = false
                        ))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to process message", e)
                _uiState.value = _uiState.value.copy(error = ChatError.fromException(e))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Heuristic to determine if the agent response is a file path (Artifact).
     * In the future, AgentResult should be updated to return Artifacts directly.
     */
    private fun parseArtifactFromResponse(response: String): Artifact? {
        val trimmed = response.trim()

        // Check for local file paths or URLs
        if ((trimmed.startsWith("/") && (trimmed.endsWith(".png") || trimmed.endsWith(".jpg"))) ||
            (trimmed.startsWith("http") && (trimmed.endsWith(".png") || trimmed.endsWith(".jpg")))) {
            val uri = if (trimmed.startsWith("/")) Uri.parse("file://$trimmed") else Uri.parse(trimmed)
            return Artifact.Image(
                id = java.util.UUID.randomUUID().toString(),
                uri = uri
            )
        }

        return null
    }

    fun regenerateMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            val messages = _uiState.value.messages
            val index = messages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                // Delete from DB first to ensure persistence - batch delete for efficiency
                val messagesToDelete = messages.subList(index, messages.size)
                messagesToDelete.forEach { msg ->
                    try {
                        messageDao.deleteMessage(msg.toEntity())
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to delete message: ${msg.id}", e)
                    }
                }
                val newMessages = messages.take(index)
                _uiState.value = _uiState.value.copy(messages = newMessages)
                sendMessage(newText)
            }
        }
    }

    fun retryMessage(message: ChatMessage) {
        viewModelScope.launch {
            // If it's a user message, just try sending it again
            if (message.isUser) {
                sendMessage(message.text)
            } else {
                // If it's a bot message, find the last user message and regenerate
                val messages = _uiState.value.messages
                val index = messages.indexOfFirst { it.id == message.id }
                if (index > 0) {
                    val prevMessage = messages[index - 1]
                    if (prevMessage.isUser) {
                        regenerateMessage(prevMessage.id, prevMessage.text)
                    }
                }
            }
        }
    }

    fun saveApiKey(providerId: ProviderId, apiKey: String) {
        // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
        com.shadowai.core.security.discoveredApiKey(apiKey).use { secret ->
            secretRepository.saveApiKey(providerId.name, secret)
        }
    }

    fun generateImage(params: ImageGenParams) {
        // Delegate to sendMessage which manages loading state
        val prompt = "Generate image: ${params.prompt}" +
                     if (params.negativePrompt.isNotBlank()) " (Negative: ${params.negativePrompt})" else ""
        sendMessage(prompt)
    }

    /**
     * Set shared text from Intent Share Sheet.
     * This pre-fills the chat input with text shared from another app.
     * M-19: Persists to SavedStateHandle for navigation state restoration.
     */
    fun setSharedText(text: String?) {
        _uiState.value = _uiState.value.copy(sharedText = text)
        savedStateHandle.set(KEY_SHARED_TEXT, text)
    }

    /**
     * Set shared images from Intent Share Sheet.
     * These images can be attached to messages or analyzed by the AI.
     * M-19: Persists to SavedStateHandle for navigation state restoration.
     */
    fun setSharedImages(images: List<Uri>) {
        _uiState.value = _uiState.value.copy(sharedImages = images)
        savedStateHandle.set(KEY_SHARING_ACTIVE, images.isNotEmpty())
    }

    /**
     * Clear shared content after it has been processed.
     * M-19: Clears SavedStateHandle entries.
     */
    fun clearSharedContent() {
        _uiState.value = _uiState.value.copy(
            sharedText = null,
            sharedImages = emptyList()
        )
        savedStateHandle.remove<String>(KEY_SHARED_TEXT)
        savedStateHandle.remove<Boolean>(KEY_SHARING_ACTIVE)
    }

    /**
     * Send a message with optional image attachments.
     * Processes both text and images from the share sheet.
     */
    fun sendMessageWithAttachments(text: String, imageUris: List<Uri> = emptyList()) {
        var messageText = text
        
        // Add image descriptions to the message if images are present
        if (imageUris.isNotEmpty()) {
            val imageDescription = if (imageUris.size == 1) {
                "[Shared Image]"
            } else {
                "[Shared ${imageUris.size} Images]"
            }
            messageText = if (text.isNotBlank()) {
                "$imageDescription\n\n$text"
            } else {
                imageDescription
            }
        }
        
        // Clear shared content and send the message
        clearSharedContent()
        sendMessage(messageText)
    }

    /**
     * Restores previously summarized messages back into the active chat timeline.
     */
    fun restoreSummarizedMessages(messages: List<com.shadowai.app.ui.chat.ChatMessage>) {
        if (messages.isEmpty()) return

        viewModelScope.launch {
            val restoredUiMessages = messages.map(com.shadowai.app.ui.chat.ChatMessage::toUiMessage)
            val mergedMessages = (_uiState.value.messages + restoredUiMessages)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }

            _uiState.value = _uiState.value.copy(messages = mergedMessages)

            restoredUiMessages.forEach { message ->
                try {
                    messageDao.insertMessage(message.toEntity())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist restored message: ${message.id}", e)
                }
            }

            ShadowAiWidgetProvider.updateAllWidgets(context)
        }
    }

    private suspend fun addMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(message)
        _uiState.value = _uiState.value.copy(messages = currentMessages)

        if (!message.isThinking) {
            try {
                messageDao.insertMessage(message.toEntity())
                // Trigger widget update after inserting message
                ShadowAiWidgetProvider.updateAllWidgets(context)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to persist message: ${message.id}", e)
            }
        }
    }
}
