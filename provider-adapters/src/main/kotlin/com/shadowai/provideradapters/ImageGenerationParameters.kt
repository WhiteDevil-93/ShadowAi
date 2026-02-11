package com.shadowai.provideradapters

/**
 * Sealed class representing image generation parameters for provider adapters.
 * Contains specific parameter types for different image generation modes.
 * Types aligned with Novita API expectations.
 */
sealed class ImageGenerationParameters {

    abstract val prompt: String
    abstract val negativePrompt: String
    abstract val width: Int
    abstract val height: Int
    abstract val steps: Int
    abstract val guidanceScale: Double
    abstract val seed: Long?
    abstract val sampler: String

    /**
     * Parameters for text-to-image generation.
     */
    data class TextToImageParameters(
        override val prompt: String,
        override val negativePrompt: String = "",
        override val width: Int = 512,
        override val height: Int = 512,
        override val steps: Int = 20,
        override val guidanceScale: Double = 7.5,
        override val seed: Long? = null,
        override val sampler: String = "Euler a",
        val batchSize: Int = 1
    ) : ImageGenerationParameters() {
        companion object {
            @JvmStatic
            fun fromMap(map: Map<String, *>): TextToImageParameters {
                return TextToImageParameters(
                    prompt = map["prompt"] as? String ?: "",
                    negativePrompt = map["negativePrompt"] as? String
                        ?: map["negative_prompt"] as? String ?: "",
                    width = (map["width"] as? Number)?.toInt() ?: 512,
                    height = (map["height"] as? Number)?.toInt() ?: 512,
                    steps = (map["steps"] as? Number)?.toInt()
                        ?: (map["numInferenceSteps"] as? Number)?.toInt() ?: 20,
                    guidanceScale = (map["guidanceScale"] as? Number)?.toDouble() ?: 7.5,
                    seed = (map["seed"] as? Number)?.toLong(),
                    sampler = map["sampler"] as? String ?: "Euler a",
                    batchSize = (map["batchSize"] as? Number)?.toInt() ?: 1
                )
            }
        }
    }

    /**
     * Parameters for image-to-image generation.
     */
    data class ImageToImageParameters(
        override val prompt: String,
        val initImage: String? = null,
        val strength: Double = 0.75,
        override val negativePrompt: String = "",
        override val width: Int = 512,
        override val height: Int = 512,
        override val steps: Int = 20,
        override val guidanceScale: Double = 7.5,
        override val seed: Long? = null,
        override val sampler: String = "Euler a"
    ) : ImageGenerationParameters() {
        companion object {
            @JvmStatic
            fun fromMap(map: Map<String, *>): ImageToImageParameters {
                return ImageToImageParameters(
                    prompt = map["prompt"] as? String ?: "",
                    initImage = map["initImage"] as? String
                        ?: map["image"] as? String,
                    strength = (map["strength"] as? Number)?.toDouble() ?: 0.75,
                    negativePrompt = map["negativePrompt"] as? String
                        ?: map["negative_prompt"] as? String ?: "",
                    width = (map["width"] as? Number)?.toInt() ?: 512,
                    height = (map["height"] as? Number)?.toInt() ?: 512,
                    steps = (map["steps"] as? Number)?.toInt()
                        ?: (map["numInferenceSteps"] as? Number)?.toInt() ?: 20,
                    guidanceScale = (map["guidanceScale"] as? Number)?.toDouble() ?: 7.5,
                    seed = (map["seed"] as? Number)?.toLong(),
                    sampler = map["sampler"] as? String ?: "Euler a"
                )
            }
        }
    }
}
