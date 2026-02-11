package com.shadowai.provideradapters

import com.google.gson.annotations.SerializedName

data class NovitaImageToImageRequest(
    @SerializedName("model_name") val modelName: String,
    val prompt: String,
    @SerializedName("negative_prompt") val negativePrompt: String,
    val image: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    @SerializedName("guidance_scale") val guidanceScale: Double,
    val strength: Double,
    val seed: Long?,
    val sampler: String
)
