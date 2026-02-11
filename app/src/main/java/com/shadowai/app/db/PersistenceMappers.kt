package com.shadowai.app.db

import android.net.Uri
import com.shadowai.app.ui.ChatMessage
import com.shadowai.core.Artifact

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        text = text,
        isUser = isUser,
        isThinking = isThinking,
        timestamp = timestamp,
        modelName = modelName,
        executionSource = executionSource,
        error = error,
        imageUri = imageUri
    )
}

fun ChatMessageEntity.toModel(): ChatMessage {
    val restoredArtifact = imageUri
        ?.takeIf { it.isNotBlank() }
        ?.let { uriString ->
            Artifact.Image(
                id = java.util.UUID.randomUUID().toString(),
                uri = Uri.parse(uriString)
            )
        }

    return ChatMessage(
        id = id,
        text = text,
        artifact = restoredArtifact,
        isUser = isUser,
        isThinking = isThinking,
        timestamp = timestamp,
        modelName = modelName,
        executionSource = executionSource,
        error = error
    )
}
