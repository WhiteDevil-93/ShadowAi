package com.shadowai.provideradapters

import android.util.Log
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapter for NovelAI API.
 * Supports image generation (Anime/NovelAI Diffusion models) and story/text generation.
 *
 * API Docs: https://api.novelai.net/
 */
class NovelAIAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter, StreamingProviderAdapter {

    private companion object {
        private const val TAG = "NovelAIAdapter"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 300_000L
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val DEFAULT_MODEL = "kayra-v1"
        private const val DEFAULT_IMAGE_MODEL = "nai-diffusion-3"
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.NOVELAI

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
            val apiKeySecret = config.apiKeySecret ?: return@withContext false
            val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }
            val request = Request.Builder()
                .url("${getBaseUrl()}/user/subscription")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
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
            is Transform.TextToText -> true
            is Transform.TextToImage -> true
            else -> false
        }
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToText -> 55
            is Transform.TextToImage -> 80
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
                is Transform.TextToText -> {
                    val stream = parameters["stream"] as? Boolean ?: false
                    if (stream) {
                        Result.failure(UnsupportedOperationException(
                            "Use executeStreaming for streaming responses"
                        ))
                    } else {
                        executeTextGeneration(input as String, parameters)
                    }
                }
                is Transform.TextToImage -> executeImageGeneration(input as String, parameters)
                else -> Result.failure(UnsupportedOperationException(
                    "Transform ${transform::class.simpleName} not supported by NovelAI"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(mapToDomainError(e))
        }
    }

    // ==================== Core AI Capability Methods ====================

    /**
     * Generate text/LLM response from a prompt.
     */
    suspend fun generate(
        prompt: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return executeTextGeneration(prompt, parameters)
    }

    /**
     * Generate an image from a text prompt.
     * Returns NovelAIImageResult containing the binary image data.
     */
    suspend fun generateImage(
        prompt: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<NovelAIImageResult> {
        return executeImageGeneration(prompt, parameters)
    }

    /**
     * Generate audio from text.
     * NovelAI does not support text-to-audio generation.
     */
    suspend fun generateAudio(
        text: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<ByteArray> {
        return Result.failure(
            UnsupportedOperationException("NovelAI does not support text-to-audio generation")
        )
    }

    /**
     * Generate video from text.
     * NovelAI does not support text-to-video generation.
     */
    suspend fun generateVideo(
        prompt: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<ByteArray> {
        return Result.failure(
            UnsupportedOperationException("NovelAI does not support text-to-video generation")
        )
    }

    /**
     * Generate text embeddings.
     * NovelAI does not provide a public embeddings API.
     */
    suspend fun generateEmbeddings(
        text: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<FloatArray> {
        return Result.failure(
            UnsupportedOperationException("NovelAI does not support embeddings generation")
        )
    }

    /**
     * Analyze/understand an image.
     * NovelAI does not support image-to-text (vision) capabilities.
     */
    suspend fun analyzeImage(
        image: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("NovelAI does not support image analysis/vision")
        )
    }

    /**
     * Analyze/understand a video.
     * NovelAI does not support video analysis.
     */
    suspend fun analyzeVideo(
        video: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("NovelAI does not support video analysis")
        )
    }

    /**
     * Transcribe audio to text.
     * NovelAI does not support audio transcription.
     */
    suspend fun transcribe(
        audio: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException("NovelAI does not support audio transcription")
        )
    }

    // ==================== Streaming Support ====================

    /**
     * Execute a streaming text generation request.
     * Returns a Flow of partial text responses.
     */
    override fun executeStreaming(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Flow<StreamingResponse>? {
        if (transform !is Transform.TextToText) return null

        return flow {
            val modelId = config.modelId ?: DEFAULT_MODEL
            val prompt = input as String
            val maxTokens = parameters["maxTokens"] as? Int ?: 150
            val temperature = parameters["temperature"] as? Double ?: 0.7
            val streamResponse = parameters["streamResponse"] as? Boolean ?: true

            val requestBody = NovelAIStoryRequest(
                model = modelId,
                input = prompt,
                parameters = StoryParameters(
                    maxOutputTokens = maxTokens,
                    temperature = temperature,
                    streamingResponse = streamResponse
                )
            )

            val streamApiKeySecret = config.apiKeySecret
                ?: throw NovelAIException.AuthenticationError()
            val streamApiKey = streamApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/ai/generate-stream")
                .header("Authorization", "Bearer $streamApiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val client = httpClient.newBuilder()
                .readTimeout(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

            val response = withRetryInternal { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val error = parseErrorResponse(response.code, errorBody)
                emit(StreamingResponse.Error(error))
                return@flow
            }

            response.body?.byteStream()?.use { stream ->
                stream.bufferedReader().useLines { lines ->
                    val isDone = AtomicBoolean(false)

                    lines.forEach { line ->
                        if (isDone.get()) return@forEach

                        if (line.isBlank()) return@forEach

                        try {
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6)
                                if (data == "[DONE]") {
                                    isDone.set(true)
                                    emit(StreamingResponse.Done)
                                } else {
                                    val chunk = gson.fromJson(data, NovelAIStreamChunk::class.java)
                                    val text = chunk.token ?: chunk.text
                                    if (!text.isNullOrEmpty()) {
                                        emit(StreamingResponse.Chunk(text))
                                    }
                                }
                            } else {
                                emit(StreamingResponse.Chunk(line))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse chunk: $line", e)
                        }
                    }

                    if (!isDone.get()) {
                        emit(StreamingResponse.Done)
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    // ==================== Private Implementation Methods ====================

    private suspend fun executeTextGeneration(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: DEFAULT_MODEL
            val maxTokens = parameters["maxTokens"] as? Int ?: 150
            val temperature = parameters["temperature"] as? Double ?: 0.7

            val requestBody = NovelAIStoryRequest(
                model = modelId,
                input = prompt,
                parameters = StoryParameters(
                    maxOutputTokens = maxTokens,
                    temperature = temperature
                )
            )

            val textApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(NovelAIException.AuthenticationError())
            val textApiKey = textApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/ai/generate")
                .header("Authorization", "Bearer $textApiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetryInternal { httpClient.newCall(request).execute() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            NovelAIException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, NovelAIStoryResponse::class.java)
                        val output = parsed.output
                            ?: return@withTimeoutOrNull Result.failure(
                                NovelAIException.EmptyContent()
                            )
                        Result.success(output)
                    } catch (e: Exception) {
                        Result.failure(NovelAIException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(NovelAIException.Timeout())
    }

    private suspend fun executeImageGeneration(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<NovelAIImageResult> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
            val modelId = config.modelId ?: DEFAULT_IMAGE_MODEL
            val width = parameters["width"] as? Int ?: 832
            val height = parameters["height"] as? Int ?: 1216
            val steps = parameters["steps"] as? Int ?: 28
            val scale = parameters["scale"] as? Double ?: 5.0
            val sampler = parameters["sampler"] as? String ?: "k_euler"
            val seed = parameters["seed"] as? Long
            val negativePrompt = parameters["negativePrompt"] as? String ?: ""
            val ucPreset = parameters["ucPreset"] as? Int ?: 0

            val requestBody = NovelAIImageRequest(
                input = prompt,
                model = modelId,
                parameters = ImageParameters(
                    width = width,
                    height = height,
                    steps = steps,
                    scale = scale,
                    sampler = sampler,
                    seed = seed ?: System.currentTimeMillis(),
                    negativePrompt = negativePrompt,
                    ucPreset = ucPreset
                )
            )

            val imageApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(NovelAIException.AuthenticationError())
            val imageApiKey = imageApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/ai/generate-image")
                .header("Authorization", "Bearer $imageApiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetryInternal { httpClient.newCall(request).execute() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                        ?: return@withTimeoutOrNull Result.failure(
                            NovelAIException.EmptyResponse()
                        )

                    Result.success(NovelAIImageResult(
                        imageData = bytes,
                        modelId = modelId,
                        prompt = prompt,
                        parameters = parameters
                    ))
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(NovelAIException.Timeout())
    }

    private fun getBaseUrl(): String {
        return config.baseUrl.trimEnd('/')
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
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: NovelAIException.UnknownError(-1, "Max retries exceeded")
    }

    private fun parseErrorResponse(code: Int, body: String?): NovelAIException {
        return when (code) {
            401 -> NovelAIException.AuthenticationError()
            429 -> NovelAIException.RateLimitError()
            400 -> {
                val parsed = parseNovelAIApiError(body)
                NovelAIException.BadRequest(parsed?.message, parsed?.code)
            }
            500, 502, 503, 504 -> NovelAIException.ServerError(code)
            else -> {
                val parsed = parseNovelAIApiError(body)
                NovelAIException.UnknownError(code, parsed?.message, parsed?.code)
            }
        }
    }

    /**
     * Parses NovelAI API error response JSON to extract structured error information.
     * Expected format: {"error": {"message": "...", "code": ...}, "status": ...}
     * Or: {"message": "...", "status": ...}
     */
    private fun parseNovelAIApiError(body: String?): NovelAIApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorWrapper = gson.fromJson(body, NovelAIApiErrorWrapper::class.java)
            errorWrapper?.error ?: NovelAIApiError(
                message = errorWrapper?.message,
                code = errorWrapper?.code,
                status = errorWrapper?.status
            )
        } catch (e: Exception) {
            // Fallback: try simple format
            try {
                val simpleError = gson.fromJson(body, NovelAIApiError::class.java)
                simpleError
            } catch (_: Exception) {
                NovelAIApiError(message = body, code = null, status = null)
            }
        }
    }

    /**
     * M-2 FIX: Preserve original exception context in error mapping.
     * The original exception is now passed as the cause for better debugging.
     */
    private fun mapToDomainError(e: Exception): NovelAIException {
        return when (e) {
            is IOException -> NovelAIException.NetworkError(e)
            is NovelAIException -> e
            // M-2 FIX: Pass original exception as cause instead of just message
            else -> NovelAIException.UnknownError(-1, e.message, cause = e)
        }
    }

    // ==================== Data Classes ====================

    data class NovelAIStoryRequest(
        val model: String,
        val input: String,
        val parameters: StoryParameters
    )

    data class StoryParameters(
        @SerializedName("max_output_tokens") val maxOutputTokens: Int,
        val temperature: Double,
        @SerializedName("streaming_response") val streamingResponse: Boolean = false
    )

    data class NovelAIStoryResponse(
        val output: String? = null,
        val error: String? = null
    )

    data class NovelAIStreamChunk(
        val token: String? = null,
        val text: String? = null,
        val index: Int? = null
    )

    data class NovelAIImageRequest(
        val input: String,
        val model: String,
        val parameters: ImageParameters
    )

    data class ImageParameters(
        val width: Int,
        val height: Int,
        val steps: Int,
        val scale: Double,
        val sampler: String,
        val seed: Long,
        @SerializedName("negative_prompt") val negativePrompt: String,
        @SerializedName("uc_preset") val ucPreset: Int
    )

    // ==================== Result Classes ====================

    data class NovelAIImageResult(
        val imageData: ByteArray,
        val modelId: String,
        val prompt: String,
        val parameters: Map<String, Any>
    ) {
        fun getImageBytes(): ByteArray {
            return imageData
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NovelAIImageResult

            if (!imageData.contentEquals(other.imageData)) return false
            if (modelId != other.modelId) return false
            if (prompt != other.prompt) return false
            if (parameters != other.parameters) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imageData.contentHashCode()
            result = 31 * result + modelId.hashCode()
            result = 31 * result + prompt.hashCode()
            result = 31 * result + parameters.hashCode()
            return result
        }
    }

    // ==================== Error Data Classes ====================

    /**
     * NovelAI API error response wrapper.
     * Format: {"error": {"message": "...", "code": ...}, "status": ...}
     */
    data class NovelAIApiErrorWrapper(
        val error: NovelAIApiError?,
        val message: String? = null,
        val code: String? = null,
        val status: String? = null
    )

    /**
     * Individual NovelAI API error details.
     */
    data class NovelAIApiError(
        val message: String?,
        val code: String?,
        val status: String? = null,
        val type: String? = null
    )

    // ==================== Custom Exceptions ====================

    sealed class NovelAIException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : NovelAIException(
            "Invalid API key or insufficient subscription. Please check your NovelAI settings."
        )

        class RateLimitError : NovelAIException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null
        ) : NovelAIException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code
        )

        class ServerError(code: Int) : NovelAIException(
            "NovelAI server error (HTTP $code). Please try again later."
        )

        class EmptyResponse : NovelAIException(
            "Empty response from NovelAI API"
        )

        class EmptyContent : NovelAIException(
            "Response contained no content"
        )

        class ParseError(cause: Throwable) : NovelAIException(
            "Failed to parse response: ${cause.message}",
            cause
        )

        class Timeout : NovelAIException(
            "Request timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : NovelAIException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null,
            cause: Throwable? = null
        ) : NovelAIException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            cause = cause,
            errorCode = apiErrorCode
        )
    }
}
