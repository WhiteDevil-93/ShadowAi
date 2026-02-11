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
 * Adapter for Google Gemini API.
 * Supports text generation, vision (image-to-text), multimodal, and streaming.
 *
 * API Docs: https://ai.google.dev/docs
 */
class GeminiAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        private const val TAG = "GeminiAdapter"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val STREAM_TIMEOUT_MS = 300_000L
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.GEMINI

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
            // Test with a simple models list request
            val apiKeySecret = config.apiKeySecret ?: return@withContext false
            val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }
            val request = Request.Builder()
                .url("${getBaseUrl()}/v1beta/models?key=$apiKey")
                .header("x-goog-api-key", apiKey)
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
            is Transform.TextToText -> 65
            is Transform.ImageToText -> 65
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
                    "Transform ${transform::class.simpleName} not supported by Gemini"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(mapToDomainError(e))
        }
    }

    /**
     * Execute a streaming text generation request.
     * Returns a Flow of partial text responses.
     */
    fun executeStreaming(
        prompt: String,
        parameters: Map<String, Any>
    ): Flow<StreamingResponse> = flow {
        val modelId = config.modelId ?: "gemini-1.5-pro-latest"
        val maxTokens = parameters["maxTokens"] as? Int ?: 2048
        val temperature = parameters["temperature"] as? Double ?: 0.7

        val parts = listOf(
            Part(text = prompt)
        )

        val contents = listOf(
            GeminiContent(role = "user", parts = parts)
        )

        val generationConfig = GenerationConfig(
            maxOutputTokens = maxTokens,
            temperature = temperature,
            topP = 0.95,
            topK = 1
        )

        val requestBody = GeminiRequest(
            contents = contents,
            generationConfig = generationConfig
        )

            val apiKeySecret = config.apiKeySecret
                ?: throw GeminiException.AuthenticationError()
            val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/v1beta/models/$modelId:streamGenerateContent?key=$apiKey")
                .header("x-goog-api-key", apiKey)
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
                lines.forEach { line ->
                    if (line.isBlank()) return@useLines
                    if (!line.startsWith("data: ")) return@forEach

                    val data = line.substring(6)
                    try {
                        val chunk = gson.fromJson(data, GeminiStreamChunk::class.java)
                        val text = chunk.candidates?.firstOrNull()
                            ?.content?.parts?.firstOrNull()
                            ?.text
                        if (!text.isNullOrEmpty()) {
                            emit(StreamingResponse.Chunk(text))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse chunk: $data", e)
                    }
                }
                emit(StreamingResponse.Done)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeTextGeneration(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "gemini-1.5-pro-latest"
            val maxTokens = parameters["maxTokens"] as? Int ?: 2048
            val temperature = parameters["temperature"] as? Double ?: 0.7

            val parts = listOf(
                Part(text = prompt)
            )

            val contents = listOf(
                GeminiContent(role = "user", parts = parts)
            )

            val generationConfig = GenerationConfig(
                maxOutputTokens = maxTokens,
                temperature = temperature,
                topP = 0.95,
                topK = 1
            )

            val requestBody = GeminiRequest(
                contents = contents,
                generationConfig = generationConfig
            )

            val textApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(GeminiException.AuthenticationError())
            val textApiKey = textApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/v1beta/models/$modelId:generateContent?key=$textApiKey")
                .header("x-goog-api-key", textApiKey)
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetry { httpClient.newCall(request).execute() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            GeminiException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, GeminiResponse::class.java)
                        val candidate = parsed.candidates?.firstOrNull()

                        // Check for safety blocks first
                        val blockReason = candidate?.finishReason
                        if (blockReason == "SAFETY") {
                            return@withTimeoutOrNull Result.failure(
                                GeminiException.SafetyBlocked()
                            )
                        }

                        val content = candidate?.content?.parts?.firstOrNull()?.text
                            ?: return@withTimeoutOrNull Result.failure(
                                GeminiException.EmptyContent()
                            )

                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(GeminiException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(GeminiException.Timeout())
    }

    /**
     * Execute a vision/multimodal request that accepts an image as input.
     * Supports File, ByteArray, or base64-encoded String as image input.
     */
    private suspend fun executeVisionRequest(
        input: Any,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "gemini-1.5-pro-latest"
            val maxTokens = parameters["maxTokens"] as? Int ?: 2048
            val temperature = parameters["temperature"] as? Double ?: 0.7

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
                    GeminiException.BadRequest("No valid image provided. Expected File, ByteArray, or base64 string.")
                )
            }

            // Detect MIME type and strip data URL prefix
            val mimeType = when {
                base64Image.contains("png") -> "image/png"
                base64Image.contains("jpeg") -> "image/jpeg"
                base64Image.contains("jpg") -> "image/jpeg"
                base64Image.contains("gif") -> "image/gif"
                base64Image.contains("webp") -> "image/webp"
                else -> "image/jpeg"
            }
            val base64Data = base64Image.substringAfter(",")

            val parts = listOf(
                Part(text = prompt),
                Part(
                    inlineData = InlineData(
                        mimeType = mimeType,
                        data = base64Data
                    )
                )
            )

            val contents = listOf(
                GeminiContent(role = "user", parts = parts)
            )

            val generationConfig = GenerationConfig(
                maxOutputTokens = maxTokens,
                temperature = temperature,
                topP = 0.95,
                topK = 1
            )

            val requestBody = GeminiRequest(
                contents = contents,
                generationConfig = generationConfig
            )

            val visionApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(GeminiException.AuthenticationError())
            val visionApiKey = visionApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/v1beta/models/$modelId:generateContent?key=$visionApiKey")
                .header("x-goog-api-key", visionApiKey)
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetry { httpClient.newCall(request).execute() }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            GeminiException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, GeminiResponse::class.java)
                        val candidate = parsed.candidates?.firstOrNull()

                        // Check for safety blocks first
                        val blockReason = candidate?.finishReason
                        if (blockReason == "SAFETY") {
                            return@withTimeoutOrNull Result.failure(
                                GeminiException.SafetyBlocked()
                            )
                        }

                        val content = candidate?.content?.parts?.firstOrNull()?.text
                            ?: return@withTimeoutOrNull Result.failure(
                                GeminiException.EmptyContent()
                            )

                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(GeminiException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(GeminiException.Timeout())
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

    private fun parseErrorResponse(code: Int, body: String?): GeminiException {
        return when (code) {
            401 -> GeminiException.AuthenticationError()
            429 -> GeminiException.RateLimitError()
            400 -> {
                val parsedError = parseGeminiApiError(body)
                GeminiException.BadRequest(parsedError?.message, parsedError?.code, parsedError?.status)
            }
            500, 502, 503, 504 -> GeminiException.ServerError(code)
            else -> {
                val parsedError = parseGeminiApiError(body)
                GeminiException.UnknownError(code, parsedError?.message, parsedError?.code, parsedError?.status)
            }
        }
    }

    /**
     * Parses Gemini API error response to extract structured error information.
     * Expected format: {"error": {"code": "...", "message": "...", "status": "..."}}
     */
    private fun parseGeminiApiError(body: String?): GeminiApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorResponse = gson.fromJson(body, GeminiApiErrorResponse::class.java)
            errorResponse?.error
        } catch (e: Exception) {
            // Fallback: try simple format
            try {
                val simpleError = gson.fromJson(body, GeminiSimpleError::class.java)
                GeminiApiError(
                    message = simpleError.message ?: simpleError.error?.message ?: body,
                    code = simpleError.code ?: simpleError.error?.code,
                    status = simpleError.status
                )
            } catch (_: Exception) {
                GeminiApiError(message = body, code = null, status = null)
            }
        }
    }

    private fun mapToDomainError(e: Exception): GeminiException {
        return when (e) {
            is IOException -> GeminiException.NetworkError(e)
            is GeminiException -> e
            else -> GeminiException.UnknownError(-1, e.message)
        }
    }

    // ==================== Data Classes ====================

    data class GeminiRequest(
        val contents: List<GeminiContent>,
        @SerializedName("generationConfig") val generationConfig: GenerationConfig
    )

    data class GeminiContent(
        val role: String,
        val parts: List<Part>
    )

    data class Part(
        val text: String? = null,
        @SerializedName("inline_data") val inlineData: InlineData? = null
    )

    data class InlineData(
        @SerializedName("mime_type") val mimeType: String,
        val data: String
    )

    data class GenerationConfig(
        @SerializedName("maxOutputTokens") val maxOutputTokens: Int,
        val temperature: Double,
        val topP: Double,
        val topK: Int
    )

    data class GeminiResponse(
        val candidates: List<Candidate>? = null,
        @SerializedName("promptFeedback") val promptFeedback: PromptFeedback? = null
    )

    data class Candidate(
        val content: ResponseContent? = null,
        @SerializedName("finishReason") val finishReason: String? = null,
        val index: Int
    )

    data class ResponseContent(
        val role: String,
        val parts: List<ResponsePart>
    )

    data class ResponsePart(
        val text: String? = null
    )

    data class PromptFeedback(
        @SerializedName("blockReason") val blockReason: String? = null
    )

    data class GeminiStreamChunk(
        val candidates: List<Candidate>? = null
    )

    // ==================== Streaming Response Sealed Class ====================

    sealed class StreamingResponse {
        data class Chunk(val content: String) : StreamingResponse()
        object Done : StreamingResponse()
        data class Error(val exception: Throwable) : StreamingResponse()
    }

    // ==================== Error Data Classes ====================

    /**
     * Gemini API error response wrapper.
     * Format: {"error": {"code": "...", "message": "...", "status": "..."}}
     */
    data class GeminiApiErrorResponse(
        val error: GeminiApiError?
    )

    /**
     * Individual Gemini API error details.
     */
    data class GeminiApiError(
        val code: String?,
        val message: String?,
        val status: String?,
        val details: List<GeminiErrorDetail>? = null
    )

    /**
     * Gemini error detail entry.
     */
    data class GeminiErrorDetail(
        val type: String?,
        val reason: String?,
        val domain: String?,
        val metadata: Map<String, String>? = null
    )

    /**
     * Fallback simple error format.
     */
    data class GeminiSimpleError(
        val code: String?,
        val message: String?,
        val status: String?,
        val error: GeminiApiError?
    )

    // ==================== Custom Exceptions ====================

    sealed class GeminiException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null,
        val errorStatus: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : GeminiException(
            "Invalid API key. Please check your Gemini API key in settings."
        )

        class RateLimitError : GeminiException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null,
            status: String? = null
        ) : GeminiException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code,
            errorStatus = status
        )

        class ServerError(code: Int) : GeminiException(
            "Gemini server error (HTTP $code). Please try again later."
        )

        class EmptyResponse : GeminiException(
            "Empty response from Gemini API"
        )

        class EmptyContent : GeminiException(
            "Response contained no content"
        )

        class SafetyBlocked : GeminiException(
            "Request blocked by safety filters. Please try a different prompt."
        )

        class ParseError(cause: Throwable) : GeminiException(
            "Failed to parse response: ${cause.message}",
            cause
        )

        class Timeout : GeminiException(
            "Request timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : GeminiException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null,
            errorStatus: String? = null
        ) : GeminiException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            errorCode = apiErrorCode,
            errorStatus = errorStatus
        )
    }

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
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: GeminiException.UnknownError(-1, "Max retries exceeded")
    }
}
