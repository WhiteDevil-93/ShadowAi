package com.shadowai.provideradapters

import com.google.gson.annotations.SerializedName

data class NovitaTextToImageRequest(
    @SerializedName("model_name") val modelName: String,
    val prompt: String,
    @SerializedName("negative_prompt") val negativePrompt: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    @SerializedName("guidance_scale") val guidanceScale: Double,
    val seed: Long?,
    val sampler: String,
    @SerializedName("batch_size") val batchSize: Int
)
