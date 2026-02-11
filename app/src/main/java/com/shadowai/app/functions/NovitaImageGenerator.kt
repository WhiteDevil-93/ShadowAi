package com.shadowai.app.functions

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated generator for Novita API.
 * Orchestrates the image generation workflow using FunctionExecutor.
 */
@Singleton
class NovitaImageGenerator @Inject constructor(
    private val functionExecutor: FunctionExecutor
) {
    suspend fun generate(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        params: ImageGenParams
    ): Result<Bitmap> {
        val imagesResult = functionExecutor.runNovitaImage(baseUrl, apiKey, modelId, params)
        return imagesResult.fold(
            onSuccess = { images ->
                if (images.isNotEmpty()) {
                    val firstImage = images.first()
                    val bytesResult = functionExecutor.downloadImageBytes(firstImage.url)
                    bytesResult.fold(
                        onSuccess = { bytes ->
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                Result.success(bitmap)
                            } else {
                                Result.failure(IllegalStateException("Failed to decode Novita image."))
                            }
                        },
                        onFailure = { Result.failure(it) }
                    )
                } else {
                    Result.failure(IllegalStateException("Novita returned no images."))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
}
