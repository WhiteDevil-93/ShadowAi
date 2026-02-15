package com.shadowai.app.ui.chat

import com.shadowai.app.ui.ChatMessage as UiChatMessage
import com.shadowai.core.Artifact
import com.shadowai.app.tasks.Plan
import com.shadowai.app.execution.DeviceAction
import java.util.UUID

/**
 * Chat message type used in UI chat components.
 * Compatible with the core ui.ChatMessage but with role enum for summarization.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val role: ChatRole = ChatRole.ASSISTANT,
    val artifact: Artifact? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String>? = null,
    val isThinking: Boolean = false,
    val modelName: String? = null,
    val executionSource: String? = null,
    val plan: Plan? = null,
    val proposedAction: DeviceAction? = null,
    val error: String? = null
) {
    companion object {
        /**
         * Convert from ui.ChatMessage to chat.ChatMessage
         */
        fun fromUiMessage(message: UiChatMessage): ChatMessage {
            return ChatMessage(
                id = message.id,
                content = message.text,
                role = if (message.isUser) ChatRole.USER else ChatRole.ASSISTANT,
                artifact = message.artifact,
                timestamp = message.timestamp,
                modelName = message.modelName,
                executionSource = message.executionSource,
                plan = message.plan,
                proposedAction = message.proposedAction,
                error = message.error,
                isThinking = message.isThinking
            )
        }

        /**
         * Convert from chat.ChatMessage to ui.ChatMessage
         */
        fun toUiMessage(message: ChatMessage): UiChatMessage {
            return UiChatMessage(
                id = message.id,
                text = message.content,
                artifact = message.artifact,
                isUser = message.role == ChatRole.USER,
                isThinking = message.isThinking,
                timestamp = message.timestamp,
                modelName = message.modelName,
                executionSource = message.executionSource,
                plan = message.plan,
                proposedAction = message.proposedAction,
                error = message.error
            )
        }
    }

    val timestampString: String
        get() = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))

    val hasText: Boolean
        get() = content.isNotBlank() || (artifact is Artifact.Text)

    val hasImage: Boolean
        get() = artifact is Artifact.Image

    val hasArtifact: Boolean
        get() = artifact != null

    val hasProposedAction: Boolean
        get() = proposedAction != null

    val isError: Boolean
        get() = !error.isNullOrBlank()

    val displayText: String
        get() = error ?: when (artifact) {
            is Artifact.Text -> artifact.content
            else -> content
        }

    val imageUri: String?
        get() = when (artifact) {
            is Artifact.Image -> artifact.uri.toString()
            else -> null
        }
}