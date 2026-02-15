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
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapter for Ollama Cloud API.
 * Implements OpenAI-compatible chat completions endpoint at /v1/chat/completions.
 * Supports streaming and non-streaming text generation.
 *
 * Endpoint: https://ollama.com/api
 * API Docs: https://docs.ollama.com/api/introduction
 */
class OllamaCloudAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        private const val TAG = "OllamaCloudAdapter"
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val STREAM_TIMEOUT_MS = 300_000L
        private const val OLLAMA_API_VERSION = "v1"
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.OLLAMA_CLOUD

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
            // Test with a minimal models list request
            val apiKeySecret = config.apiKeySecret ?: return@withContext false
            val apiKey = apiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }
            val request = Request.Builder()
                .url("${getBaseUrl()}/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "Availability check failed", e)
            false
        }
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToText -> true
            is Transform.TextToImage -> false
            is Transform.ImageToText -> false
            else -> false
        }
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToText -> 60 // Medium priority, below local but above some cloud
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
                    val prompt = input as String
                    val stream = parameters["stream"] as? Boolean ?: false

                    if (stream) {
                        // For streaming, we return a placeholder - actual streaming uses executeStreaming
                        Result.failure(UnsupportedOperationException(
                            "Use executeStreaming for streaming responses"
                        ))
                    } else {
                        executeChatCompletion(prompt, parameters)
                    }
                }
                else -> Result.failure(UnsupportedOperationException(
                    "Transform ${transform::class.simpleName} not supported by Ollama Cloud"
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
        val modelId = config.modelId ?: "qwen3-coder:30b"
        val maxTokens = parameters["maxTokens"] as? Int ?: 2048
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

        val streamApiKeySecret = config.apiKeySecret
            ?: throw OllamaCloudException.AuthenticationError()
        val streamApiKey = streamApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

        val request = Request.Builder()
            .url("${getBaseUrl()}/chat/completions")
            .header("Authorization", "Bearer $streamApiKey")
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val client = httpClient.newBuilder()
            .readTimeout(STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        val call = client.newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            val error = parseErrorResponse(response.code, errorBody)
            throw error
        }

        response.body?.byteStream()?.use { stream ->
            stream.bufferedReader().useLines { lines ->
                var isDone = AtomicBoolean(false)

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
                                    Log.w(TAG, "Failed to parse chunk: $data", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeChatCompletion(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
            val modelId = config.modelId ?: "qwen3-coder:30b"
            val maxTokens = parameters["maxTokens"] as? Int ?: 2048
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

            val textApiKeySecret = config.apiKeySecret
                ?: return@withTimeoutOrNull Result.failure(OllamaCloudException.AuthenticationError())
            val textApiKey = textApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("${getBaseUrl()}/chat/completions")
                .header("Authorization", "Bearer $textApiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetry {
                httpClient.newCall(request).execute()
            }
            response.use { resp ->
                if (resp.isSuccessful) {
                    val responseBody = resp.body?.string()
                        ?: return@withTimeoutOrNull Result.failure(
                            OllamaCloudException.EmptyResponse()
                        )

                    try {
                        val parsed = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                        val content = parsed.choices.firstOrNull()?.message?.content
                            ?: return@withTimeoutOrNull Result.failure(
                                OllamaCloudException.EmptyContent()
                            )
                        Result.success(content)
                    } catch (e: Exception) {
                        Result.failure(OllamaCloudException.ParseError(e))
                    }
                } else {
                    val errorBody = resp.body?.string()
                    Result.failure(parseErrorResponse(resp.code, errorBody))
                }
            }
        } ?: Result.failure(OllamaCloudException.Timeout())
    }

    private fun getBaseUrl(): String {
        return config.baseUrl.trimEnd('/')
    }

    private fun parseErrorResponse(code: Int, body: String?): OllamaCloudException {
        return when (code) {
            401 -> OllamaCloudException.AuthenticationError()
            429 -> OllamaCloudException.RateLimitError()
            400 -> {
                val parsed = parseOllamaCloudApiError(body)
                OllamaCloudException.BadRequest(parsed?.message, parsed?.code)
            }
            500, 502, 503, 504 -> OllamaCloudException.ServerError(code)
            else -> {
                val parsed = parseOllamaCloudApiError(body)
                OllamaCloudException.UnknownError(code, parsed?.message, parsed?.code)
            }
        }
    }

    /**
     * Parses Ollama Cloud API error response JSON to extract structured error information.
     * Expected format: {"error": {"message": "...", "code": "...", "type": "..."}}
     * Or: {"message": "...", "error": "..."}
     */
    private fun parseOllamaCloudApiError(body: String?): OllamaCloudApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorResponse = gson.fromJson(body, OllamaCloudApiErrorResponse::class.java)
            errorResponse?.error
        } catch (e: Exception) {
            // Fallback: try simple format
            try {
                val simpleError = gson.fromJson(body, OllamaCloudApiError::class.java)
                simpleError
            } catch (_: Exception) {
                OllamaCloudApiError(message = body, code = null, type = null)
            }
        }
    }

    /**
     * M-2 FIX: Preserve original exception context in error mapping.
     * The original exception is now passed as the cause for better debugging.
     */
    private fun mapToDomainError(e: Exception): OllamaCloudException {
        return when (e) {
            is IOException -> OllamaCloudException.NetworkError(e)
            is OllamaCloudException -> e
            // M-2 FIX: Pass original exception as cause instead of just message
            else -> OllamaCloudException.UnknownError(-1, e.message, cause = e)
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
     * Ollama Cloud API error response wrapper.
     * Format: {"error": {"message": "...", "code": "...", "type": "..."}}
     */
    data class OllamaCloudApiErrorResponse(
        val error: OllamaCloudApiError?
    )

    /**
     * Individual Ollama Cloud API error details.
     */
    data class OllamaCloudApiError(
        val message: String?,
        val code: String?,
        val type: String?
    )

    // ==================== Custom Exceptions ====================

    sealed class OllamaCloudException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : OllamaCloudException(
            "Invalid API key. Please check your Ollama Cloud API key in settings."
        )

        class RateLimitError : OllamaCloudException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null
        ) : OllamaCloudException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code
        )

        class ServerError(code: Int) : OllamaCloudException(
            "Ollama Cloud server error (HTTP $code). Please try again later."
        )

        class EmptyResponse : OllamaCloudException(
            "Empty response from Ollama Cloud API"
        )

        class EmptyContent : OllamaCloudException(
            "Response contained no content"
        )

        class ParseError(cause: Throwable) : OllamaCloudException(
            "Failed to parse response: ${cause.message}",
            cause
        )

        class Timeout : OllamaCloudException(
            "Request timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : OllamaCloudException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null,
            cause: Throwable? = null
        ) : OllamaCloudException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            cause = cause,
            errorCode = apiErrorCode
        )
    }
}
