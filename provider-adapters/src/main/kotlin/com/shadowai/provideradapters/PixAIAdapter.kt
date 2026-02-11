package com.shadowai.provideradapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.shadowai.provideradapters.ImageGenerationParameters.TextToImageParameters
import com.shadowai.provideradapters.ImageGenerationParameters.ImageToImageParameters

/**
 * Adapter for PixAI API.
 * Supports anime-style image generation through PixAI.art.
 *
 * API Docs: https://api.pixai.art/docs
 */
class PixAIAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        private const val TAG = "PixAIAdapter"
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.PIXAI

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val valid = validateConfig()
            isInitialized = valid
            valid
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            isInitialized = false
            false
        }
    }

    override suspend fun validateConfig(): Boolean {
        return config.baseUrl.isNotBlank() &&
               config.apiKeySecret != null
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext false
        }
        try {
            // Check API health by fetching models
            val apiKeySecret = config.apiKeySecret ?: return@withContext false
            val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }
            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = withRetryInternal { httpClient.newCall(request).execute() }
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "Availability check failed", e)
            false
        }
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToImage -> true
            is Transform.ImageToImage -> true
            else -> false
        }
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToImage -> 85
            is Transform.ImageToImage -> 85
            else -> 0
        }
    }

    override suspend fun execute(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext Result.failure(IllegalStateException("Adapter not initialized. Call initialize() first."))
        }
        try {
            when (transform) {
                is Transform.TextToImage -> executeTextToImage(input as String, parameters)
                is Transform.ImageToImage -> executeImageToImage(input, parameters)
                else -> Result.failure(UnsupportedOperationException(
                    "Transform ${transform::class.simpleName} not supported by PixAI"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(mapToDomainError(e))
        }
    }

    // ==================== Core AI Capability Methods ====================

    /**
     * Generate text/LLM response.
     * PixAI does not support text generation (image generation only).
     */
    suspend fun generate(
        prompt: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("PixAI only supports image generation, not text/LLM")
        )
    }

    /**
     * Generate an image from a text prompt.
     */
    suspend fun generateImage(
        prompt: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<PixAIImageResult> {
        return execute(Transform.TextToImage(), prompt, parameters) as Result<PixAIImageResult>
    }

    /**
     * Generate audio from text.
     * PixAI does not support text-to-audio generation.
     */
    suspend fun generateAudio(
        text: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<ByteArray> {
        return Result.failure(
            UnsupportedOperationException("PixAI does not support text-to-audio generation")
        )
    }

    /**
     * Generate video from text.
     * PixAI does not support text-to-video generation.
     */
    suspend fun generateVideo(
        prompt: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<ByteArray> {
        return Result.failure(
            UnsupportedOperationException("PixAI does not support text-to-video generation")
        )
    }

    /**
     * Generate text embeddings.
     * PixAI does not provide an embeddings API.
     */
    suspend fun generateEmbeddings(
        text: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<FloatArray> {
        return Result.failure(
            UnsupportedOperationException("PixAI does not support embeddings generation")
        )
    }

    /**
     * Analyze/understand an image.
     * PixAI does not support image-to-text vision capabilities.
     */
    suspend fun analyzeImage(
        image: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("PixAI does not support image analysis/vision")
        )
    }

    /**
     * Analyze/understand a video.
     * PixAI does not support video analysis.
     */
    suspend fun analyzeVideo(
        video: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("PixAI does not support video analysis")
        )
    }

    /**
     * Transcribe audio to text.
     * PixAI does not support audio transcription.
     */
    suspend fun transcribe(
        audio: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("PixAI does not support audio transcription")
        )
    }

    // ==================== Private Implementation Methods ====================

    private suspend fun executeTextToImage(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<PixAIImageResult> = withContext(Dispatchers.IO) {
        val modelId = config.modelId ?: "default"
        val params = try {
            TextToImageParameters.fromMap(parameters)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(PixAIException.BadRequest(e.message))
        }
        val width = params.width
        val height = params.height
        val steps = params.steps
        val guidanceScale = params.guidanceScale
        val seed = params.seed
        val negativePrompt = params.negativePrompt
        val sampler = params.sampler
        val loraId = parameters["loraId"] as? String
        val controlNetImage = parameters["controlNetImage"] as? String
        val controlNetType = parameters["controlNetType"] as? String

        val requestBody = PixAIGenerationRequest(
            modelId = modelId,
            prompt = prompt,
            negativePrompt = negativePrompt,
            width = width,
            height = height,
            steps = steps,
            guidanceScale = guidanceScale,
            seed = seed,
            sampler = sampler,
            loraId = loraId,
            controlNet = if (controlNetImage != null) {
                PixAIControlNet(
                    image = controlNetImage,
                    type = controlNetType ?: "canny"
                )
            } else null
        )

        val txt2imgApiKeySecret = config.apiKeySecret
            ?: return@withContext Result.failure(PixAIException.AuthenticationError())
        val txt2imgApiKey = txt2imgApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

        val request = Request.Builder()
            .url("${getBaseUrl()}/v1/generation")
            .header("Authorization", "Bearer $txt2imgApiKey")
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val response = withRetryInternal { httpClient.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string()
                return@withContext Result.failure(parseErrorResponse(resp.code, errorBody))
            }

            val responseBody = resp.body?.string()
                ?: return@withContext Result.failure(PixAIException.EmptyResponse())

            try {
                val parsed = gson.fromJson(responseBody, PixAIGenerationResponse::class.java)
                val jobId = parsed.jobId
                    ?: return@withContext Result.failure(PixAIException.EmptyContent())

                // Poll for completion (timeout handled by pollForJobCompletion via config.timeoutSeconds)
                pollForJobCompletion(jobId, txt2imgApiKey)
            } catch (e: Exception) {
                Result.failure(PixAIException.ParseError(e))
            }
        }
    }

    private suspend fun executeImageToImage(
        input: Any,
        parameters: Map<String, Any>
    ): Result<PixAIImageResult> = withContext(Dispatchers.IO) {
        val modelId = config.modelId ?: "default"
        val prompt = when (input) {
            is Pair<*, *> -> input.first as? String ?: ""
            is Map<*, *> -> input["prompt"] as? String ?: ""
            else -> ""
        }
        val imageData = when (input) {
            is Pair<*, *> -> input.second
            is Map<*, *> -> input["image"]
            else -> input
        }

        val params = try {
            ImageToImageParameters.fromMap(parameters)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(PixAIException.BadRequest(e.message))
        }
        val width = params.width
        val height = params.height
        val steps = params.steps
        val guidanceScale = params.guidanceScale
        val strength = params.strength
        val seed = params.seed
        val negativePrompt = params.negativePrompt
        val sampler = params.sampler

        // Encode image to base64
        val base64Image = when (imageData) {
            is java.io.File -> encodeImageToBase64(imageData.readBytes())
            is ByteArray -> encodeImageToBase64(imageData)
            is String -> if (imageData.startsWith("data:image/")) imageData else "data:image/jpeg;base64,$imageData"
            else -> return@withContext Result.failure(
                PixAIException.BadRequest("No valid image provided. Expected File, ByteArray, or base64 string.")
            )
        }

        val requestBody = PixAIImg2ImgRequest(
            modelId = modelId,
            prompt = prompt,
            negativePrompt = negativePrompt,
            image = base64Image,
            width = width,
            height = height,
            steps = steps,
            guidanceScale = guidanceScale,
            strength = strength,
            seed = seed,
            sampler = sampler
        )

        val img2imgApiKeySecret = config.apiKeySecret
            ?: return@withContext Result.failure(PixAIException.AuthenticationError())
        val img2imgApiKey = img2imgApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

        val request = Request.Builder()
            .url("${getBaseUrl()}/v1/generation/img2img")
            .header("Authorization", "Bearer $img2imgApiKey")
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val response = withRetryInternal { httpClient.newCall(request).execute() }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string()
                return@withContext Result.failure(parseErrorResponse(resp.code, errorBody))
            }

            val responseBody = resp.body?.string()
                ?: return@withContext Result.failure(PixAIException.EmptyResponse())

            try {
                val parsed = gson.fromJson(responseBody, PixAIGenerationResponse::class.java)
                val jobId = parsed.jobId
                    ?: return@withContext Result.failure(PixAIException.EmptyContent())

                // Poll for completion (timeout handled by pollForJobCompletion via config.timeoutSeconds)
                pollForJobCompletion(jobId, img2imgApiKey)
            } catch (e: Exception) {
                Result.failure(PixAIException.ParseError(e))
            }
        }
    }

    private suspend fun pollForJobCompletion(jobId: String, apiKey: String): Result<PixAIImageResult> = withContext(Dispatchers.IO) {
        val timeout = config.timeoutSeconds * 1000L
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/generation/$jobId")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = withRetryInternal { httpClient.newCall(request).execute() }

            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                try {
                    val result = gson.fromJson(body, PixAIJobResult::class.java)

                    when (result.status?.uppercase()) {
                        "COMPLETED", "SUCCESS" -> {
                            val media = result.medias?.firstOrNull()
                                ?: return@withContext Result.failure(
                                    PixAIException.EmptyContent()
                                )
                            return@withContext Result.success(PixAIImageResult(
                                imageUrl = media.url,
                                imageUrls = result.medias.map { it.url },
                                modelId = result.modelId ?: "default",
                                parameters = mapOf(
                                    "prompt" to (result.prompt ?: ""),
                                    "width" to (result.width ?: 1024),
                                    "height" to (result.height ?: 1024)
                                )
                            ))
                        }
                        "FAILED", "ERROR", "CANCELLED" -> {
                            return@withContext Result.failure(
                                PixAIException.UnknownError(-1, result.error ?: "Job failed")
                            )
                        }
                        else -> {
                            // Still processing, continue polling
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse job result", e)
                }
            }

            delay(POLL_INTERVAL_MS)
        }

        Result.failure(PixAIException.Timeout())
    }

    private fun getBaseUrl(): String {
        return config.baseUrl.trimEnd('/')
    }

    private fun encodeImageToBase64(imageBytes: ByteArray): String {
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val mimeType = when {
            imageBytes.size >= 2 && imageBytes[0] == 0xFF.toByte() && imageBytes[1] == 0xD8.toByte() -> "image/jpeg"
            imageBytes.size >= 8 && imageBytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
            imageBytes.size >= 4 && imageBytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x47, 0x49, 0x46, 0x38)) -> "image/gif"
            imageBytes.size >= 2 && imageBytes.copyOfRange(0, 2).contentEquals(byteArrayOf(0x42, 0x4D)) -> "image/bmp"
            else -> "image/jpeg"
        }
        return "data:$mimeType;base64,$base64"
    }

    private suspend fun <T> withRetryInternal(
        maxRetries: Int = MAX_RETRIES,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: PixAIException.UnknownError(-1, "Max retries exceeded")
    }

    private fun parseErrorResponse(code: Int, body: String?): PixAIException {
        return when (code) {
            401 -> PixAIException.AuthenticationError()
            429 -> PixAIException.RateLimitError()
            400 -> {
                val parsed = parsePixAIApiError(body)
                PixAIException.BadRequest(parsed?.message, parsed?.code)
            }
            500, 502, 503, 504 -> PixAIException.ServerError(code)
            else -> {
                val parsed = parsePixAIApiError(body)
                PixAIException.UnknownError(code, parsed?.message, parsed?.code)
            }
        }
    }

    /**
     * Parses PixAI API error response JSON to extract structured error information.
     * Expected format: {"code": ..., "message": "...", "status": "..."}
     * Or: {"error": {"code": ..., "message": "..."}}
     */
    private fun parsePixAIApiError(body: String?): PixAIApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorWrapper = gson.fromJson(body, PixAIApiErrorWrapper::class.java)
            errorWrapper?.error ?: PixAIApiError(
                code = errorWrapper?.code?.toString(),
                message = errorWrapper?.message,
                status = errorWrapper?.status
            )
        } catch (e: Exception) {
            // Fallback: try simple format
            try {
                val simpleError = gson.fromJson(body, PixAIApiError::class.java)
                simpleError
            } catch (_: Exception) {
                PixAIApiError(message = body, code = null, status = null)
            }
        }
    }

    private fun mapToDomainError(e: Exception): PixAIException {
        return when (e) {
            is IOException -> PixAIException.NetworkError(e)
            is PixAIException -> e
            else -> PixAIException.UnknownError(-1, e.message)
        }
    }

    // ==================== Data Classes ====================

    data class PixAIGenerationRequest(
        @SerializedName("model_id") val modelId: String,
        val prompt: String,
        @SerializedName("negative_prompt") val negativePrompt: String,
        val width: Int,
        val height: Int,
        val steps: Int,
        @SerializedName("guidance_scale") val guidanceScale: Double,
        val seed: Long?,
        val sampler: String,
        @SerializedName("lora_id") val loraId: String? = null,
        @SerializedName("control_net") val controlNet: PixAIControlNet? = null
    )

    data class PixAIControlNet(
        val image: String,
        val type: String
    )

    data class PixAIImg2ImgRequest(
        @SerializedName("model_id") val modelId: String,
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

    data class PixAIGenerationResponse(
        @SerializedName("job_id") val jobId: String?,
        val code: Int?,
        val message: String?,
        val status: String?
    )

    data class PixAIJobResult(
        val id: String?,
        val status: String?,
        @SerializedName("model_id") val modelId: String?,
        val prompt: String?,
        val width: Int?,
        val height: Int?,
        val medias: List<PixAIMedia>?,
        val error: String?
    )

    data class PixAIMedia(
        val id: String?,
        val url: String,
        val type: String?,
        val width: Int?,
        val height: Int?
    )

    // ==================== Result Classes ====================

    data class PixAIImageResult(
        val imageUrl: String,
        val imageUrls: List<String>,
        val modelId: String,
        val parameters: Map<String, Any>
    ) {
        fun getPrimaryUrl(): String = imageUrl
        fun getAllUrls(): List<String> = imageUrls
    }

    // ==================== Error Data Classes ====================

    /**
     * PixAI API error response wrapper.
     * Format: {"code": ..., "message": "...", "status": "..."}
     */
    data class PixAIApiErrorWrapper(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null,
        val error: PixAIApiError? = null
    )

    /**
     * Individual PixAI API error details.
     */
    data class PixAIApiError(
        val code: String?,
        val message: String?,
        val status: String? = null
    )

    // ==================== Custom Exceptions ====================

    sealed class PixAIException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : PixAIException(
            "Invalid API key. Please check your PixAI API key in settings."
        )

        class RateLimitError : PixAIException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null
        ) : PixAIException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code
        )

        class ServerError(code: Int) : PixAIException(
            "PixAI server error (HTTP $code). Please try again later."
        )

        class EmptyResponse : PixAIException(
            "Empty response from PixAI API"
        )

        class EmptyContent : PixAIException(
            "Response contained no content"
        )

        class ParseError(cause: Throwable) : PixAIException(
            "Failed to parse response: ${cause.message}",
            cause
        )

        class Timeout : PixAIException(
            "Request timed out. The job may still be processing on the server."
        )

        class NetworkError(cause: IOException) : PixAIException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null
        ) : PixAIException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            errorCode = apiErrorCode
        )
    }
}
