package com.shadowai.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Represents a message in a conversation with an AI model.
 * This is a modality-agnostic message representation that can be used
 * across different AI providers and inference engines.
 *
 * Enhanced for multimodal support: content can be plain text or a list of content parts
 * (text + images, audio, etc.) for vision models like GPT-4V, Claude 3, etc.
 */
@Parcelize
data class Message(
    val role: Role,
    val content: @RawValue MessageContent,
    val metadata: @RawValue Map<String, Any> = emptyMap()
) : Parcelable {

    /**
     * Message role enumeration.
     */
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    /**
     * Sealed class representing message content.
     * Can be simple text or multimodal (text + images).
     */
    sealed class MessageContent : Parcelable {
        /**
         * Plain text content (traditional text-only messages).
         */
        @Parcelize
        data class Text(val text: String) : MessageContent()

        /**
         * Multimodal content with multiple parts (text, images, etc.).
         */
        @Parcelize
        data class Multimodal(val parts: List<ContentPart>) : MessageContent()
    }

    /**
     * Individual content part for multimodal messages.
     */
    sealed class ContentPart : Parcelable {
        /**
         * Text content part.
         */
        @Parcelize
        data class Text(val text: String) : ContentPart()

        /**
         * Image content part with URI reference.
         */
        @Parcelize
        data class Image(
            val uri: String,
            val mimeType: String = "image/jpeg",
            val detail: ImageDetail = ImageDetail.AUTO
        ) : ContentPart()

        /**
         * Binary data part (for embeddings, audio bytes, etc.).
         */
        @Parcelize
        data class Binary(
            val data: ByteArray,
            val mimeType: String
        ) : ContentPart() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Binary) return false
                return data.contentEquals(other.data) && mimeType == other.mimeType
            }

            override fun hashCode(): Int {
                var result = data.contentHashCode()
                result = 31 * result + mimeType.hashCode()
                return result
            }
        }
    }

    /**
     * Image detail level for vision models.
     */
    enum class ImageDetail {
        LOW,    // Low resolution, faster processing
        HIGH,   // High resolution, more detail
        AUTO    // Model decides based on image size
    }

    companion object {
        /**
         * Create a simple text message from the user.
         */
        fun user(text: String, metadata: Map<String, Any> = emptyMap()) =
            Message(Role.USER, MessageContent.Text(text), metadata)

        /**
         * Create a multimodal user message with text and images.
         */
        fun userWithImage(text: String, imageUri: String, metadata: Map<String, Any> = emptyMap()) =
            Message(
                Role.USER,
                MessageContent.Multimodal(
                    listOf(ContentPart.Text(text), ContentPart.Image(imageUri))
                ),
                metadata
            )

        /**
         * Create a simple text message from the assistant.
         */
        fun assistant(text: String, metadata: Map<String, Any> = emptyMap()) =
            Message(Role.ASSISTANT, MessageContent.Text(text), metadata)

        /**
         * Create a system message.
         */
        fun system(text: String, metadata: Map<String, Any> = emptyMap()) =
            Message(Role.SYSTEM, MessageContent.Text(text), metadata)

        /**
         * Create a tool/function result message.
         */
        fun toolResult(text: String, toolCallId: String, metadata: Map<String, Any> = emptyMap()) =
            Message(
                Role.TOOL,
                MessageContent.Text(text),
                metadata + ("tool_call_id" to toolCallId)
            )
    }

    /**
     * Get the text content if this is a simple text message.
     * Returns null for multimodal messages.
     */
    fun getText(): String? = when (content) {
        is MessageContent.Text -> content.text
        is MessageContent.Multimodal -> content.parts
            .filterIsInstance<ContentPart.Text>()
            .joinToString(" ") { it.text }
            .takeIf { it.isNotBlank() }
    }

    /**
     * Check if this message has image content.
     */
    fun hasImages(): Boolean = when (content) {
        is MessageContent.Text -> false
        is MessageContent.Multimodal -> content.parts.any { it is ContentPart.Image }
    }

    /**
     * Get all image URIs in this message.
     */
    fun getImageUris(): List<String> = when (content) {
        is MessageContent.Text -> emptyList()
        is MessageContent.Multimodal -> content.parts
            .filterIsInstance<ContentPart.Image>()
            .map { it.uri }
    }

    /**
     * Returns true if this message is multimodal (contains images or other media).
     */
    fun isMultimodal(): Boolean = content is MessageContent.Multimodal
}
