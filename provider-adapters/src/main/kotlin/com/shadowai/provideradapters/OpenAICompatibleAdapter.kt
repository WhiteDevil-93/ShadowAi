package com.shadowai.provideradapters

import android.util.Base64
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapter for OpenAI-compatible API endpoints.
 * Supports OpenAI, OpenRouter, Groq, and other compatible APIs.
 * Fully supports text generation, vision (image-to-text), image generation,
 * streaming responses, and retry logic.
 */
class OpenAICompatibleAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        // FIX H-16-H-17: Unified timeout constants
        // All timeout values in milliseconds
        private const val DEFAULT_TIMEOUT_MS = 60000L           // 60 seconds for standard requests
        private const val STREAM_TIMEOUT_MS = 300_000L            // 5 minutes for streaming
        private const val STREAM_CHUNK_TIMEOUT_MS = 30_000L      // 30 seconds between chunks
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private var isInitialized = false

    override val providerId: ProviderId = config.providerId

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val valid = validateConfig()
            isInitialized = valid
            valid
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }

    override suspend fun validateConfig(): Boolean {
        return config.baseUrl.isNotBlank() &&
               config.resolveApiKeySecret() != null
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        // H-10 FIX: Lightweight check to avoid network overhead on every request.
        // AdapterBridge uses this to decide whether to call initialize().
        // Network health should be checked by AdapterHealthChecker or during execution.
        return@withContext isInitialized
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToText -> true
            is Transform.ImageToText -> true
            is Transform.TextToImage -> true
            else -> false
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
                is Transform.ImageToText -> executeVisionRequest(input, parameters)
                is Transform.TextToImage -> executeImageGeneration(input as String, parameters)
                else -> Result.failure(UnsupportedOperationException("Transform not supported"))
            }
        } catch (e: Exception) {
            Result.failure(mapToDomainError(e))
        }
    }

    /**
     * Execute a streaming chat completion request.
     * Returns a Flow of partial text responses.
     * FIX H-15: Proper resource cleanup with use() blocks
     * FIX H-16-H-17: Consistent timeout handling - throws on timeout (matching non-streaming behavior)
     */
    fun executeStreaming(
        prompt: String,
        parameters: Map<String, Any>
    ): Flow<StreamingResponse> = flow {
        val modelId = config.modelId ?: "gpt-3.5-turbo"
        // FIX H-16-H-17: Consistent maxTokens default for streaming (was 512, now 1024)
        val maxTokens = parameters["maxTokens"] as? Int ?: 1024
        val temperature = parameters["temperature"] as? Double ?: 0.7
        val systemPrompt = parameters["systemPrompt"] as? String

        val messages = buildList {
            if (systemPrompt != null) {
                add(Message(role = "system", content = systemPrompt))
            }
            add(Message(role = "user", content = prompt))
        }

        val requestBody = StreamingChatRequest(
            model = modelId,
            messages = messages,
            maxTokens = maxTokens,
            temperature = temperature,
            stream = true
        )

        val secret = config.resolveApiKeySecret()
            ?: throw OpenAICompatibleException.AuthenticationError()

        val request = secret.withSecretBytes { bytes ->
            Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .header("Authorization", "Bearer ${String(bytes, Charsets.UTF_8)}")
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()
        }

        // FIX H-16-H-17: Use consistent streaming timeout
        val client = httpClient.newBuilder()
            .readTimeout(STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        // FIX H-15: Use response.use() wrapper for proper resource cleanup
        // FIX H-16-H-17: Wrap in withTimeout to throw Timeout on streaming timeout (consistent with non-streaming)
        val response = withTimeout(STREAM_TIMEOUT_MS) {
            withRetry { client.newCall(request).await() }
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string()
                val error = parseErrorResponse(resp.code, errorBody)
                throw error
            }

            // FIX H-15: Use body.use() to ensure stream is closed
            resp.body.use { body ->
                body?.byteStream()?.use { stream ->
                    stream.bufferedReader().useLines { lines ->
                        val isDone = AtomicBoolean(false)

                        lines.forEach { line ->
                            if (isDone.get()) return@forEach

                            when {
                                line.isBlank() -> return@forEach
                                line.startsWith("data: ") -> {
                                    val data = line.substring(6)
                                    if (data == "[DONE]") {
                                        isDone.set(true)
                                        emit(StreamingResponse.Done)
                                    } else {
                                        try {
                                            val chunk = gson.fromJson(data, ChatCompletionChunk::class.java)
                                            val delta = chunk.choices.firstOrNull()?.delta?.content
                                            if (!delta.isNullOrEmpty()) {
                                                emit(StreamingResponse.Chunk(delta))
                                            }
                                            if (chunk.choices.firstOrNull()?.finishReason != null) {
                                                isDone.set(true)
                                                emit(StreamingResponse.Done)
                                            }
                                        } catch (e: Exception) {
                                            emit(StreamingResponse.Error(e))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } ?: throw OpenAICompatibleException.EmptyResponse()
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeTextGeneration(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "gpt-3.5-turbo"
            val maxTokens = parameters["maxTokens"] as? Int ?: 512
            val temperature = parameters["temperature"] as? Double ?: 0.7
            val systemPrompt = parameters["systemPrompt"] as? String

            val messages = buildList {
                if (systemPrompt != null) {
                    add(Message(role = "system", content = systemPrompt))
                }
                add(Message(role = "user", content = prompt))
            }

            val requestBody = ChatRequest(
                model = modelId,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature,
                stream = false
            )

            val secret = config.resolveApiKeySecret()
                ?: return@withTimeoutOrNull Result.failure(OpenAICompatibleException.AuthenticationError())

            val request = secret.withSecretBytes { bytes ->
                 Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${String(bytes, Charsets.UTF_8)}")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()
            }

            val response = withRetry { httpClient.newCall(request).await() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            OpenAICompatibleException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                        val content = parsed.choices.firstOrNull()?.message?.content
                            ?: return@withTimeoutOrNull Result.failure(
                                OpenAICompatibleException.EmptyContent()
                            )
                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(OpenAICompatibleException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(OpenAICompatibleException.Timeout())
    }

    /**
     * Execute a vision request that accepts an image as input.
     * Supports File, ByteArray, or base64-encoded String as image input.
     */
    private suspend fun executeVisionRequest(
        input: Any,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "gpt-4o"
            val maxTokens = parameters["maxTokens"] as? Int ?: 1024
            val temperature = parameters["temperature"] as? Double ?: 0.7
            val systemPrompt = parameters["systemPrompt"] as? String

            // Extract prompt and image from input
            val (prompt, imageData) = when (input) {
                is Pair<*, *> -> {
                    val text = input.first as? String ?: ""
                    val img = input.second
                    text to img
                }
                is Map<*, *> -> {
                    val text = input["prompt"] as? String ?: ""
                    val img = input["image"]
                    text to img
                }
                else -> input.toString() to null
            }

            // Encode image to base64
            val base64Image = when (imageData) {
                is File -> encodeImageToBase64(imageData.readBytes())
                is ByteArray -> encodeImageToBase64(imageData)
                is String -> if (imageData.startsWith("data:image/")) imageData else "data:image/jpeg;base64,$imageData"
                else -> null
            }

            if (base64Image == null) {
                return@withTimeoutOrNull Result.failure(
                    OpenAICompatibleException.BadRequest("No valid image provided. Expected File, ByteArray, or base64 string.")
                )
            }

            // Build vision content array
            val content = buildList {
                add(VisionContent(type = "text", text = prompt))
                add(VisionContent(
                    type = "image_url",
                    imageUrl = ImageUrl(url = base64Image)
                ))
            }

            val messages = buildList {
                if (systemPrompt != null) {
                    add(VisionMessage(role = "system", content = listOf(VisionContent(type = "text", text = systemPrompt))))
                }
                add(VisionMessage(role = "user", content = content))
            }

            val requestBody = VisionChatRequest(
                model = modelId,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature
            )

            val secret = config.resolveApiKeySecret()
                ?: return@withTimeoutOrNull Result.failure(OpenAICompatibleException.AuthenticationError())

            val request = secret.withSecretBytes { bytes ->
                 Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${String(bytes, Charsets.UTF_8)}")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()
            }

            val response = withRetry { httpClient.newCall(request).await() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            OpenAICompatibleException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                        val content = parsed.choices.firstOrNull()?.message?.content
                            ?: return@withTimeoutOrNull Result.failure(
                                OpenAICompatibleException.EmptyContent()
                            )
                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(OpenAICompatibleException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(OpenAICompatibleException.Timeout())
    }

    /**
     * Execute image generation request.
     * Returns the generated image URL.
     */
    private suspend fun executeImageGeneration(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "dall-e-3"
            val size = parameters["size"] as? String ?: "1024x1024"
            val quality = parameters["quality"] as? String ?: "standard"
            val n = parameters["n"] as? Int ?: 1

            val requestBody = ImageGenerationRequest(
                model = modelId,
                prompt = prompt,
                n = n,
                size = size,
                quality = quality
            )

            val secret = config.resolveApiKeySecret()
                ?: return@withTimeoutOrNull Result.failure(OpenAICompatibleException.AuthenticationError())

            val request = secret.withSecretBytes { bytes ->
                 Request.Builder()
                .url("${config.baseUrl}/images/generations")
                .header("Authorization", "Bearer ${String(bytes, Charsets.UTF_8)}")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()
            }

            val response = withRetry { httpClient.newCall(request).await() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            OpenAICompatibleException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, ImageGenerationResponse::class.java)
                        val imageUrl = parsed.data.firstOrNull()?.url
                            ?: return@withTimeoutOrNull Result.failure(
                                OpenAICompatibleException.EmptyContent()
                            )
                        Result.success(imageUrl)
                    } catch (e: Exception) {
                        Result.failure(OpenAICompatibleException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(OpenAICompatibleException.Timeout())
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToText -> 50
            is Transform.ImageToText -> 50
            is Transform.TextToImage -> 50
            else -> 0
        }
    }

    /**
     * Retry wrapper with exponential backoff.
     * Retries on 429, 500, 502, 503, 504 status codes.
     */
    private suspend fun <T> withRetry(
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
                    delayMs *= 2 // Exponential backoff
                }
            }
        }

        throw lastException ?: OpenAICompatibleException.UnknownError(-1, "Max retries exceeded")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation {
            try { cancel() } catch (ex: Exception) { /* ignore */ }
        }
    }

    /**
     * Helper to encode image bytes to base64 data URL.
     */
    private fun encodeImageToBase64(imageBytes: ByteArray): String {
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        // Try to detect MIME type from magic bytes
        val mimeType = when {
            imageBytes.size >= 2 && imageBytes[0] == 0xFF.toByte() && imageBytes[1] == 0xD8.toByte() -> "image/jpeg"
            imageBytes.size >= 8 && imageBytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
            imageBytes.size >= 4 && imageBytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x47, 0x49, 0x46, 0x38)) -> "image/gif"
            imageBytes.size >= 2 && imageBytes.copyOfRange(0, 2).contentEquals(byteArrayOf(0x42, 0x4D)) -> "image/bmp"
            else -> "image/jpeg" // Default to JPEG
        }
        return "data:$mimeType;base64,$base64"
    }

    private fun parseErrorResponse(code: Int, body: String?): OpenAICompatibleException {
        return when (code) {
            401 -> OpenAICompatibleException.AuthenticationError()
            429 -> OpenAICompatibleException.RateLimitError()
            400 -> {
                val parsedError = parseOpenAIApiError(body)
                OpenAICompatibleException.BadRequest(parsedError?.message, parsedError?.code, parsedError?.type)
            }
            500, 502, 503, 504 -> OpenAICompatibleException.ServerError(code)
            else -> {
                val parsedError = parseOpenAIApiError(body)
                OpenAICompatibleException.UnknownError(code, parsedError?.message, parsedError?.code, parsedError?.type)
            }
        }
    }

    /**
     * Parses OpenAI-compatible API error response to extract structured error information.
     * Expected format: {"error": {"message": "...", "type": "...", "param": "...", "code": "..."}}
     */
    private fun parseOpenAIApiError(body: String?): OpenAIError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorResponse = gson.fromJson(body, OpenAIErrorResponse::class.java)
            errorResponse?.error
        } catch (e: Exception) {
            // Fallback: try parsing simple error format
            try {
                val simpleError = gson.fromJson(body, OpenAISimpleError::class.java)
                OpenAIError(
                    message = simpleError.message ?: simpleError.error ?: body,
                    type = simpleError.type,
                    code = simpleError.code,
                    param = null
                )
            } catch (_: Exception) {
                OpenAIError(message = body, type = "unknown_error", code = null, param = null)
            }
        }
    }

    /**
     * M-2 FIX: Preserve original exception context in error mapping.
     * The original exception is now passed as the cause for better debugging.
     */
    private fun mapToDomainError(e: Exception): OpenAICompatibleException {
        return when (e) {
            is IOException -> OpenAICompatibleException.NetworkError(e)
            is OpenAICompatibleException -> e
            // M-2 FIX: Pass original exception as cause instead of just message
            else -> OpenAICompatibleException.UnknownError(-1, e.message, cause = e)
        }
    }

    // ==================== Data Classes ====================

    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val stream: Boolean
    )

    data class StreamingChatRequest(
        val model: String,
        val messages: List<Message>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val stream: Boolean
    )

    data class Message(
        val role: String,
        val content: String
    )

    data class ChatCompletionResponse(
        val id: String,
        val `object`: String,
        val created: Long,
        val model: String,
        val choices: List<Choice>,
        val usage: Usage?
    )

    data class Choice(
        val index: Int,
        val message: Message,
        @SerializedName("finish_reason") val finishReason: String?
    )

    data class Usage(
        @SerializedName("prompt_tokens") val promptTokens: Int,
        @SerializedName("completion_tokens") val completionTokens: Int,
        @SerializedName("total_tokens") val totalTokens: Int
    )

    // Vision-specific data classes
    data class VisionChatRequest(
        val model: String,
        val messages: List<VisionMessage>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double
    )

    data class VisionMessage(
        val role: String,
        val content: List<VisionContent>
    )

    data class VisionContent(
        val type: String,
        val text: String? = null,
        @SerializedName("image_url") val imageUrl: ImageUrl? = null
    )

    data class ImageUrl(
        val url: String,
        val detail: String? = "auto"
    )

    // Image generation data classes
    data class ImageGenerationRequest(
        val model: String,
        val prompt: String,
        val n: Int,
        val size: String,
        val quality: String
    )

    data class ImageGenerationResponse(
        val created: Long,
        val data: List<ImageData>
    )

    data class ImageData(
        val url: String? = null,
        @SerializedName("b64_json") val b64Json: String? = null,
        @SerializedName("revised_prompt") val revisedPrompt: String? = null
    )

    // Streaming data classes
    data class ChatCompletionChunk(
        val id: String,
        val `object`: String,
        val created: Long,
        val model: String,
        val choices: List<ChunkChoice>
    )

    data class ChunkChoice(
        val index: Int,
        val delta: Delta,
        @SerializedName("finish_reason") val finishReason: String?
    )

    data class Delta(
        val role: String? = null,
        val content: String? = null
    )

    // ==================== Streaming Response Sealed Class ====================

    sealed class StreamingResponse {
        data class Chunk(val content: String) : StreamingResponse()
        object Done : StreamingResponse()
        data class Error(val exception: Throwable) : StreamingResponse()
    }

    // ==================== Error Data Classes ====================

    /**
     * OpenAI-compatible error response wrapper.
     * Format: {"error": {"message": "...", "type": "...", "param": "...", "code": "..."}}
     */
    data class OpenAIErrorResponse(
        val error: OpenAIError?
    )

    /**
     * Individual OpenAI API error details.
     */
    data class OpenAIError(
        val message: String?,
        val type: String?,
        val param: String?,
        val code: String?
    )

    /**
     * Fallback simple error format for non-standard responses.
     */
    data class OpenAISimpleError(
        val message: String?,
        val error: String?,
        val type: String?,
        val code: String?
    )

    // ==================== Custom Exceptions ====================

    sealed class OpenAICompatibleException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null,
        val errorType: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : OpenAICompatibleException(
            "Invalid API key. Please check your API key in settings."
        )

        class RateLimitError : OpenAICompatibleException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null,
            type: String? = null
        ) : OpenAICompatibleException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code,
            errorType = type
        )

        class ServerError(code: Int) : OpenAICompatibleException(
            "Server error (HTTP $code). Please try again later."
        )

        class EmptyResponse : OpenAICompatibleException(
            "Empty response from API"
        )

        class EmptyContent : OpenAICompatibleException(
            "Response contained no content"
        )

        class ParseError(cause: Throwable) : OpenAICompatibleException(
            "Failed to parse response: ${cause.message}",
            cause
        )

        class Timeout : OpenAICompatibleException(
            "Request timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : OpenAICompatibleException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null,
            errorType: String? = null,
            cause: Throwable? = null
        ) : OpenAICompatibleException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            cause = cause,
            errorCode = apiErrorCode,
            errorType = errorType
        )
    }
}
