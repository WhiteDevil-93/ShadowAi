package com.shadowai.app.ui

import com.shadowai.core.Artifact

/**
 * Represents a chat message in the UI.
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "",
    val artifact: Artifact? = null,
    val isUser: Boolean,
    val isThinking: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val executionSource: String? = null,
    val plan: com.shadowai.app.tasks.Plan? = null,
    val proposedAction: com.shadowai.app.execution.DeviceAction? = null,
    val error: String? = null
) {
    val timestampString: String
        get() = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))

    val hasText: Boolean
        get() = text.isNotBlank() || (artifact is Artifact.Text)

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
            else -> text
        }

    val imageUri: String?
        get() = when (artifact) {
            is Artifact.Image -> artifact.uri.toString()
            else -> null
        }
}
