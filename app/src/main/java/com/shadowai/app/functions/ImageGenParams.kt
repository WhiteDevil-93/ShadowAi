package com.shadowai.app.functions

data class ImageGenParams(
    val prompt: String,
    val negativePrompt: String = "",
    val modelId: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val imageCount: Int = 1,
    val steps: Int = 30,
    val guidanceScale: Double = 7.5,
    val samplerName: String = "Euler a",
    val seed: Long? = null,
    val loras: List<LoraRef> = emptyList(),
    val embeddings: List<String> = emptyList(),
    val hiresFix: HiresFix? = null,
    val clipSkip: Int? = null,
    val sdVae: String? = null,
    val refinerSwitchAt: Int? = null,
    val enableTransparentBackground: Boolean = false,
    val restoreFaces: Boolean = false,
    val responseImageType: String? = null,
    val enableNsfwDetection: Boolean? = null,
    val nsfwDetectionLevel: Int? = null
)

data class LoraRef(
    val modelName: String,
    val strength: Double = 0.75
)

data class HiresFix(
    val targetWidth: Int,
    val targetHeight: Int,
    val strength: Double,
    val upscaler: String? = null
)
