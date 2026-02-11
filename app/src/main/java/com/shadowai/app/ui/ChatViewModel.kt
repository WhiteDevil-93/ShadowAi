package com.shadowai.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.agent.AgentResult
import com.shadowai.app.agent.ShadowAgent
import com.shadowai.app.db.MessageDao
import com.shadowai.app.db.toEntity
import com.shadowai.app.db.toModel
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.core.security.PromptInjectionDefense
import com.shadowai.core.ProviderId
import com.shadowai.app.providers.ProviderRepository
import com.shadowai.app.ui.workflows.ImageGenParams
import com.shadowai.core.Artifact
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val error: ChatError? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agent: ShadowAgent,
    private val messageDao: MessageDao,
    private val providerRepository: ProviderRepository,
    private val piiMaskingProcessor: PiiMaskingProcessor,
    private val promptInjectionDefense: PromptInjectionDefense
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

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
        providerRepository.saveApiKey(providerId, apiKey)
    }

    fun generateImage(params: ImageGenParams) {
        // Delegate to sendMessage which manages loading state
        val prompt = "Generate image: ${params.prompt}" +
                     if (params.negativePrompt.isNotBlank()) " (Negative: ${params.negativePrompt})" else ""
        sendMessage(prompt)
    }

    private suspend fun addMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(message)
        _uiState.value = _uiState.value.copy(messages = currentMessages)

        if (!message.isThinking) {
            try {
                messageDao.insertMessage(message.toEntity())
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to persist message: ${message.id}", e)
            }
        }
    }
}
