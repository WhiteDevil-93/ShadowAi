package com.shadowai.app.functions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated generator for PixAI/Local Stable Diffusion.
 * Orchestrates the image generation workflow using FunctionExecutor.
 */
@Singleton
class PixaiImageGenerator @Inject constructor(
    private val functionExecutor: FunctionExecutor
) {
    suspend fun generate(
        modelPath: String,
        settings: PixAiSettings
    ): Result<Bitmap> {
        val base64Result = functionExecutor.runStableDiffusion(modelPath, settings)
        return base64Result.fold(
            onSuccess = { base64 ->
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Result.success(bitmap)
                } else {
                    Result.failure(IllegalStateException("Failed to decode local image."))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
}
