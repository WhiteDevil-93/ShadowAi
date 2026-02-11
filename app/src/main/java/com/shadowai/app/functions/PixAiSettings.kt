package com.shadowai.app.functions

data class PixAiSettings(
    val prompt: String = "",
    val negativePrompt: String = "",
    val modelId: String = "",
    val loraIds: String = "",
    val vaeModelId: String = "",
    val samplingMethod: String = "Euler a",
    val samplingSteps: Int = 28,
    val cfgScale: Double = 7.5,
    val seed: Long? = null,
    val width: Int = 512,
    val height: Int = 768,
    val batchSize: Int = 1
)
