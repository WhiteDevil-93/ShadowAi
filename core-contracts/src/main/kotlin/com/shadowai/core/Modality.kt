package com.shadowai.core

/**
 * Represents the type of input/output modality for AI transformations.
 * This sealed class defines all supported modalities in the pipeline.
 */
sealed class Modality {
    /** Text content modality */
    object Text : Modality()

    /** Image content modality */
    object Image : Modality()

    /** Video content modality */
    object Video : Modality()

    /** Audio content modality */
    object Audio : Modality()

    /** Mixed content modality (multiple modalities combined) */
    object Mixed : Modality()

    /**
     * Returns the modality name as a string for display purposes.
     */
    fun getDisplayName(): String = when (this) {
        is Text -> "Text"
        is Image -> "Image"
        is Video -> "Video"
        is Audio -> "Audio"
        is Mixed -> "Mixed"
    }

    companion object {
        /**
         * Returns all available modalities.
         */
        fun all(): List<Modality> = listOf(Text, Image, Video, Audio, Mixed)

        /**
         * Returns all simple (non-mixed) modalities.
         */
        fun simple(): List<Modality> = listOf(Text, Image, Video, Audio)

        /**
         * Parses a modality from string.
         */
        fun fromString(name: String): Modality? = when (name.lowercase()) {
            "text" -> Text
            "image" -> Image
            "video" -> Video
            "audio" -> Audio
            "mixed" -> Mixed
            else -> null
        }
    }
}
