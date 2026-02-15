package com.shadowai.core

/**
 * Represents a transformation between modalities in the AI pipeline.
 * Each transform defines how content flows from one modality to another.
 *
 * Part of Phase 0: Hard Contracts.
 */
sealed class Transform {
    /**
     * Describes the transformation for display purposes.
     */
    abstract val description: String

    /** The input modality this transform accepts. */
    abstract val sourceModality: Modality

    /** The output modality this transform produces. */
    abstract val targetModality: Modality

    /** Converts text to image */
    data class TextToImage(
        override val description: String = "Convert text to image",
        override val sourceModality: Modality = Modality.Text,
        override val targetModality: Modality = Modality.Image
    ) : Transform()

    /** Converts image to video */
    data class ImageToVideo(
        override val description: String = "Convert image to video",
        override val sourceModality: Modality = Modality.Image,
        override val targetModality: Modality = Modality.Video
    ) : Transform()

    /** Converts text directly to video */
    data class TextToVideo(
        override val description: String = "Convert text to video",
        override val sourceModality: Modality = Modality.Text,
        override val targetModality: Modality = Modality.Video
    ) : Transform()

    /** Transforms image to image (e.g., style transfer, upscaling) */
    data class ImageToImage(
        override val description: String = "Transform image to image",
        override val sourceModality: Modality = Modality.Image,
        override val targetModality: Modality = Modality.Image
    ) : Transform()

    /** Transforms text to text (e.g., summarization, translation) */
    data class TextToText(
        override val description: String = "Transform text to text",
        override val sourceModality: Modality = Modality.Text,
        override val targetModality: Modality = Modality.Text
    ) : Transform()

    /** Converts text to audio (text-to-speech) */
    data class TextToAudio(
        override val description: String = "Convert text to audio",
        override val sourceModality: Modality = Modality.Text,
        override val targetModality: Modality = Modality.Audio
    ) : Transform()

    /** Converts audio to text (speech-to-text) */
    data class AudioToText(
        override val description: String = "Convert audio to text",
        override val sourceModality: Modality = Modality.Audio,
        override val targetModality: Modality = Modality.Text
    ) : Transform()

    /** Analyzes image and returns text description */
    data class ImageToText(
        override val description: String = "Analyze image and generate text",
        override val sourceModality: Modality = Modality.Image,
        override val targetModality: Modality = Modality.Text
    ) : Transform()

    /** Analyzes video and returns text description or highlights */
    data class VideoToText(
        override val description: String = "Analyze video and generate text",
        override val sourceModality: Modality = Modality.Video,
        override val targetModality: Modality = Modality.Text
    ) : Transform()

    /** Converts text to embeddings for semantic search or analysis */
    data class TextToEmbeddings(
        override val description: String = "Convert text to embeddings",
        override val sourceModality: Modality = Modality.Text,
        override val targetModality: Modality = Modality.Mixed
    ) : Transform()

    /** Converts embeddings back to text (e.g., reconstruction or generation) */
    data class EmbeddingsToText(
        override val description: String = "Convert embeddings to text",
        override val sourceModality: Modality = Modality.Mixed,
        override val targetModality: Modality = Modality.Text
    ) : Transform()
}
