package com.shadowai.provideradapters

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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapter for Anthropic Claude API.
 * Supports text generation, vision (image-to-text), and streaming.
 *
 * API Docs: https://docs.anthropic.com/claude/reference/messages_post
 */
class AnthropicAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        private const val TAG = "AnthropicAdapter"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val STREAM_TIMEOUT_MS = 300_000L
        private const val ANTHROPIC_VERSION = "2024-01-01"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.ANTHROPIC

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
            // Quick models list check
            val apiKeySecret = config.apiKeySecret ?: return@withContext false
            val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }
            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .get()
                .build()

            val response = withRetry { httpClient.newCall(request).execute() }
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "Availability check failed", e)
            false
        }
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToText -> true
            is Transform.ImageToText -> true
            else -> false
        }
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToText -> 70
            is Transform.ImageToText -> 70
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
                is Transform.ImageToText -> executeVisionRequest(input, parameters)
                else -> Result.failure(UnsupportedOperationException(
                    "Transform ${transform::class.simpleName} not supported by Anthropic"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(mapToDomainError(e))
        }
    }

    /**
     * Execute a streaming chat completion request.
     * Returns a Flow of partial text responses.
     */
    fun executeStreaming(
        prompt: String,
        parameters: Map<String, Any>
    ): Flow<StreamingResponse> = flow {
        val modelId = config.modelId ?: "claude-3-sonnet-20240229"
        val maxTokens = parameters["maxTokens"] as? Int ?: 4096
        val temperature = parameters["temperature"] as? Double ?: 0.7
        val systemPrompt = parameters["systemPrompt"] as? String

        val messages = buildList {
            add(Message(role = "user", content = prompt))
        }

        val requestBody = MessagesRequest(
            model = modelId,
            messages = messages,
            maxTokens = maxTokens,
            temperature = temperature,
            system = systemPrompt,
            stream = true
        )

        val apiKeySecret = config.apiKeySecret
            ?: throw AnthropicException.AuthenticationError()
        val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

        val request = Request.Builder()
            .url("${getBaseUrl()}/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val client = httpClient.newBuilder()
            .readTimeout(STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        val response = withRetry { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            val error = parseErrorResponse(response.code, errorBody)
            throw error
        }

        response.body?.byteStream()?.use { stream ->
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
                                    val chunk = gson.fromJson(data, StreamEvent::class.java)
                                    when (chunk.type) {
                                        "content_block_delta" -> {
                                            val text = chunk.delta?.text
                                            if (!text.isNullOrEmpty()) {
                                                emit(StreamingResponse.Chunk(text))
                                            }
                                        }
                                        "message_stop" -> {
                                            isDone.set(true)
                                            emit(StreamingResponse.Done)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse chunk: $data", e)
                                }
                            }
                        }
                        line.startsWith("event: ") -> {
                            // Event type indicator, skip
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeTextGeneration(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "claude-3-sonnet-20240229"
            val maxTokens = parameters["maxTokens"] as? Int ?: 4096
            val temperature = parameters["temperature"] as? Double ?: 0.7
            val systemPrompt = parameters["systemPrompt"] as? String

            val messages = buildList {
                add(Message(role = "user", content = prompt))
            }

            val requestBody = MessagesRequest(
                model = modelId,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature,
                system = systemPrompt,
                stream = false
            )

            val textApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(AnthropicException.AuthenticationError())
            val textApiKey = textApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/messages")
                .header("x-api-key", textApiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetry { httpClient.newCall(request).execute() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            AnthropicException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, MessagesResponse::class.java)
                        val content = parsed.content.firstOrNull { it.type == "text" }?.text
                            ?: return@withTimeoutOrNull Result.failure(
                                AnthropicException.EmptyContent()
                            )
                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(AnthropicException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(AnthropicException.Timeout())
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
            val modelId = config.modelId ?: "claude-3-opus-20240229"
            val maxTokens = parameters["maxTokens"] as? Int ?: 4096
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
                is java.io.File -> encodeImageToBase64(imageData.readBytes())
                is ByteArray -> encodeImageToBase64(imageData)
                is String -> if (imageData.startsWith("data:image/")) imageData else "data:image/jpeg;base64,$imageData"
                else -> null
            }

            if (base64Image == null) {
                return@withTimeoutOrNull Result.failure(
                    AnthropicException.BadRequest("No valid image provided. Expected File, ByteArray, or base64 string.")
                )
            }

            // Detect MIME type
            val mimeType = when {
                base64Image.contains("png") -> "image/png"
                base64Image.contains("jpeg") -> "image/jpeg"
                base64Image.contains("jpg") -> "image/jpeg"
                base64Image.contains("gif") -> "image/gif"
                base64Image.contains("webp") -> "image/webp"
                else -> "image/jpeg"
            }

            // Strip data URL prefix for Anthropic
            val base64Data = base64Image.substringAfter(",")

            val content = buildList {
                add(VisionContent(type = "text", text = prompt))
                add(VisionContent(
                    type = "image",
                    source = ImageSource(
                        type = "base64",
                        mediaType = mimeType,
                        data = base64Data
                    )
                ))
            }

            val messages = listOf(
                VisionMessage(role = "user", content = content)
            )

            val requestBody = VisionMessagesRequest(
                model = modelId,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature,
                system = systemPrompt,
                stream = false
            )

            val visionApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(AnthropicException.AuthenticationError())
            val visionApiKey = visionApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/messages")
                .header("x-api-key", visionApiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetry { httpClient.newCall(request).execute() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            AnthropicException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, MessagesResponse::class.java)
                        val content = parsed.content.firstOrNull { it.type == "text" }?.text
                            ?: return@withTimeoutOrNull Result.failure(
                                AnthropicException.EmptyContent()
                            )
                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(AnthropicException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(AnthropicException.Timeout())
    }

    private fun getBaseUrl(): String {
        return config.baseUrl.trimEnd('/')
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

    private fun parseErrorResponse(code: Int, body: String?): AnthropicException {
        return when (code) {
            401 -> AnthropicException.AuthenticationError()
            429 -> AnthropicException.RateLimitError()
            400 -> {
                val parsedError = parseAnthropicApiError(body)
                AnthropicException.BadRequest(parsedError?.message, parsedError?.type)
            }
            500, 502, 503, 504 -> AnthropicException.ServerError(code)
            else -> {
                val parsedError = parseAnthropicApiError(body)
                AnthropicException.UnknownError(code, parsedError?.message, parsedError?.type)
            }
        }
    }

    /**
     * Parses Anthropic API error response to extract structured error information.
     * Expected format: {"error": {"type": "...", "message": "..."}}
     * Or: {"type": "error", "error": {"type": "...", "message": "..."}}
     */
    private fun parseAnthropicApiError(body: String?): AnthropicApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorWrapper = gson.fromJson(body, AnthropicApiErrorWrapper::class.java)
            errorWrapper?.error
        } catch (e: Exception) {
            // Try alternative format
            try {
                val altWrapper = gson.fromJson(body, AnthropicAltErrorWrapper::class.java)
                if (altWrapper?.type == "error") {
                    altWrapper.error
                } else {
                    AnthropicApiError(
                        type = altWrapper?.type,
                        message = body
                    )
                }
            } catch (_: Exception) {
                AnthropicApiError(type = "unknown", message = body)
            }
        }
    }

    private fun mapToDomainError(e: Exception): AnthropicException {
        return when (e) {
            is IOException -> AnthropicException.NetworkError(e)
            is AnthropicException -> e
            else -> AnthropicException.UnknownError(-1, e.message)
        }
    }

    // ==================== Data Classes ====================

    data class Message(
        val role: String,
        val content: String
    )

    data class MessagesRequest(
        val model: String,
        val messages: List<Message>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val system: String? = null,
        val stream: Boolean = false
    )

    data class VisionContent(
        val type: String,
        val text: String? = null,
        val source: ImageSource? = null
    )

    data class ImageSource(
        val type: String,
        @SerializedName("media_type") val mediaType: String,
        val data: String
    )

    data class VisionMessage(
        val role: String,
        val content: List<VisionContent>
    )

    data class VisionMessagesRequest(
        val model: String,
        val messages: List<VisionMessage>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val system: String? = null,
        val stream: Boolean = false
    )

    data class MessagesResponse(
        val id: String,
        val type: String,
        val role: String,
        val model: String,
        @SerializedName("stop_reason") val stopReason: String?,
        val content: List<ContentBlock>
    )

    data class ContentBlock(
        val type: String,
        val text: String? = null
    )

    data class StreamEvent(
        val type: String,
        val delta: Delta? = null
    )

    data class Delta(
        val type: String? = null,
        val text: String? = null
    )

    // ==================== Streaming Response Sealed Class ====================

    sealed class StreamingResponse {
        data class Chunk(val content: String) : StreamingResponse()
        object Done : StreamingResponse()
        data class Error(val exception: Throwable) : StreamingResponse()
    }

    // ==================== Error Data Classes ====================

    /**
     * Anthropic API error response wrapper.
     * Format: {"error": {"type": "...", "message": "..."}}
     */
    data class AnthropicApiErrorWrapper(
        val error: AnthropicApiError?
    )

    /**
     * Alternative Anthropic error format.
     * Format: {"type": "error", "error": {"type": "...", "message": "..."}}
     */
    data class AnthropicAltErrorWrapper(
        val type: String?,
        val error: AnthropicApiError?
    )

    /**
     * Individual Anthropic API error details.
     */
    data class AnthropicApiError(
        val type: String?,
        val message: String?
    )

    // ==================== Custom Exceptions ====================

    sealed class AnthropicException(
        message: String,
        cause: Throwable? = null,
        val errorType: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : AnthropicException(
            "Invalid API key. Please check your Anthropic API key in settings."
        )

        class RateLimitError : AnthropicException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            type: String? = null
        ) : AnthropicException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorType = type
        )

        class ServerError(code: Int) : AnthropicException(
            "Anthropic server error (HTTP $code). Please try again later."
        )

        class EmptyResponse : AnthropicException(
            "Empty response from Anthropic API"
        )

        class EmptyContent : AnthropicException(
            "Response contained no content"
        )

        class ParseError(cause: Throwable) : AnthropicException(
            "Failed to parse response: ${cause.message}",
            cause
        )

        class Timeout : AnthropicException(
            "Request timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : AnthropicException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            errorType: String? = null
        ) : AnthropicException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            errorType = errorType
        )
    }
}
