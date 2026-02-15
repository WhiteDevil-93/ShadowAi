package com.shadowai.provideradapters

import android.util.Log
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.shadowai.provideradapters.ImageGenerationParameters.TextToImageParameters
import com.shadowai.provideradapters.ImageGenerationParameters.ImageToImageParameters

/**
 * Adapter for Replicate API.
 * Supports running various AI models via Replicate's infrastructure.
 */
class ReplicateAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        private const val TAG = "ReplicateAdapter"
        private const val DEFAULT_BASE_URL = "https://api.replicate.com/v1"
        private const val POLL_INTERVAL_MS = 1500L
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.REPLICATE

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
        return config.apiKeySecret != null
    }

    override suspend fun isAvailable(): Boolean {
        return isInitialized
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        // Replicate can run many different types of models
        return when (transform) {
            is Transform.TextToText -> true
            is Transform.TextToImage -> true
            is Transform.ImageToText -> true
            is Transform.ImageToImage -> true
            is Transform.TextToAudio -> true
            is Transform.AudioToText -> true
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
            val modelVersion = config.modelId
                ?: return@withContext Result.failure(IllegalStateException("Model version required for Replicate"))

            val inputPayload = buildInputPayload(transform, input, parameters)

            val payload = mapOf(
                "version" to modelVersion,
                "input" to inputPayload
            )

            val baseUrl = config.baseUrl.ifBlank { DEFAULT_BASE_URL }
            val replicateApiKeySecret = config.apiKeySecret
                ?: return@withContext Result.failure(IllegalStateException("API key required for Replicate"))
            val replicateApiKey = replicateApiKeySecret.withSecretBytes { String(it, Charsets.UTF_8) }

            val request = Request.Builder()
                .url("$baseUrl/predictions")
                .header("Authorization", "Token $replicateApiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                .build()

            val response = withRetry {
                httpClient.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                return@withContext Result.failure(parseErrorResponse(response.code, errorBody))
            }

            val responseBody = response.body?.string()
            @Suppress("UNCHECKED_CAST")
            val json = gson.fromJson(responseBody, Map::class.java) as? Map<String, Any>

            val status = json?.get("status") as? String
            if (status == "succeeded") {
                // Immediate result
                return@withContext Result.success(extractOutput(json))
            }

            // Need to poll for result
            @Suppress("UNCHECKED_CAST")
            val urls = json?.get("urls") as? Map<String, Any>
            val getUrl = urls?.get("get") as? String
                ?: return@withContext Result.failure(Exception("No prediction URL returned"))

            pollForResult(getUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildInputPayload(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Map<String, Any> {
        val baseInput = mutableMapOf<String, Any>()

        when (transform) {
            is Transform.TextToText -> {
                baseInput["prompt"] = input.toString()
                parameters["max_tokens"]?.let { baseInput["max_tokens"] = it }
                parameters["temperature"]?.let { baseInput["temperature"] = it }
                parameters["system_prompt"]?.let { baseInput["system_prompt"] = it }
            }
            is Transform.TextToImage -> {
                val txt2imgParams = try {
                    TextToImageParameters.fromMap(parameters)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid text-to-image parameters: ${e.message}")
                }
                baseInput["prompt"] = input.toString()
                baseInput["width"] = txt2imgParams.width
                baseInput["height"] = txt2imgParams.height
                baseInput["num_inference_steps"] = txt2imgParams.steps
                baseInput["guidance_scale"] = txt2imgParams.guidanceScale
                txt2imgParams.negativePrompt.takeIf { it.isNotBlank() }?.let { baseInput["negative_prompt"] = it }
                txt2imgParams.seed?.let { baseInput["seed"] = it }
            }
            is Transform.ImageToText -> {
                baseInput["image"] = input.toString() // URL or base64
                parameters["prompt"]?.let { baseInput["prompt"] = it }
            }
            is Transform.ImageToImage -> {
                val img2imgParams = try {
                    ImageToImageParameters.fromMap(parameters)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid image-to-image parameters: ${e.message}")
                }
                baseInput["image"] = input.toString()
                baseInput["prompt"] = when (input) {
                    is Pair<*, *> -> input.first as? String ?: ""
                    is Map<*, *> -> input["prompt"] as? String ?: ""
                    else -> ""
                }
                baseInput["width"] = img2imgParams.width
                baseInput["height"] = img2imgParams.height
                baseInput["strength"] = img2imgParams.strength
                baseInput["num_inference_steps"] = img2imgParams.steps
                baseInput["guidance_scale"] = img2imgParams.guidanceScale
                img2imgParams.negativePrompt.takeIf { it.isNotBlank() }?.let { baseInput["negative_prompt"] = it }
                img2imgParams.seed?.let { baseInput["seed"] = it }
            }
            is Transform.TextToAudio -> {
                baseInput["text"] = input.toString()
                parameters["voice"]?.let { baseInput["voice"] = it }
            }
            is Transform.AudioToText -> {
                baseInput["audio"] = input.toString()
                parameters["language"]?.let { baseInput["language"] = it }
            }
            else -> {
                baseInput["input"] = input
            }
        }

        // Add any additional parameters not already handled
        parameters.forEach { (key, value) ->
            if (key !in baseInput) {
                baseInput[key] = value
            }
        }

        return baseInput
    }

    private suspend fun pollForResult(predictionUrl: String): Result<Any> = withContext(Dispatchers.IO) {
        val timeout = config.timeoutSeconds * 1000L
        val startTime = System.currentTimeMillis()
        val pollApiKeySecret = config.apiKeySecret
        val pollApiKey = pollApiKeySecret?.withSecretBytes { String(it, Charsets.UTF_8) }

        while (System.currentTimeMillis() - startTime < timeout) {
            val request = Request.Builder()
                .url(predictionUrl)
                .header("Authorization", "Token ${pollApiKey ?: ""}")
                .get()
                .build()

            val response = withRetry {
                httpClient.newCall(request).execute()
            }
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                val json = gson.fromJson(response.body?.string(), Map::class.java) as? Map<String, Any>
                val status = json?.get("status") as? String

                when (status) {
                    "succeeded" -> {
                        return@withContext Result.success(extractOutput(json))
                    }
                    "failed" -> {
                        val errorMessage = json["error"] as? String ?: "Prediction failed"
                        return@withContext Result.failure(ReplicateException.PredictionFailed(errorMessage))
                    }
                    "canceled" -> {
                        return@withContext Result.failure(ReplicateException.PredictionCanceled())
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }

        Result.failure(ReplicateException.Timeout())
    }

    private fun extractOutput(json: Map<String, Any>?): ReplicateOutput {
        val output = json?.get("output")
        val metrics = json?.get("metrics") as? Map<*, *>

        return ReplicateOutput(
            output = output,
            predictionId = json?.get("id") as? String ?: "",
            model = json?.get("model") as? String ?: "",
            version = json?.get("version") as? String ?: "",
            predictTimeSeconds = (metrics?.get("predict_time") as? Number)?.toDouble()
        )
    }

    override fun getPriority(transform: Transform): Int {
        // Replicate is a general-purpose platform, give moderate priority
        return when (transform) {
            is Transform.TextToImage -> 70
            is Transform.ImageToImage -> 70
            is Transform.TextToText -> 50
            else -> 40
        }
    }

    private fun parseErrorResponse(code: Int, body: String?): ReplicateException {
        return when (code) {
            401 -> ReplicateException.AuthenticationError()
            429 -> ReplicateException.RateLimitError()
            400 -> {
                val parsed = parseReplicateApiError(body)
                ReplicateException.BadRequest(parsed?.message, parsed?.code)
            }
            500, 502, 503, 504 -> ReplicateException.ServerError(code)
            else -> {
                val parsed = parseReplicateApiError(body)
                ReplicateException.UnknownError(code, parsed?.message, parsed?.code)
            }
        }
    }

    /**
     * Parses Replicate API error response JSON to extract structured error information.
     * Expected format: {"error": "...", "detail": "...", "status": ...}
     */
    private fun parseReplicateApiError(body: String?): ReplicateApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorResponse = gson.fromJson(body, ReplicateApiErrorWrapper::class.java)
            errorResponse?.error ?: ReplicateApiError(
                message = errorResponse?.detail ?: body,
                code = errorResponse?.status?.toString(),
                detail = errorResponse?.detail
            )
        } catch (e: Exception) {
            // Fallback: try simple string error
            ReplicateApiError(message = body, code = null, detail = body)
        }
    }

    // ==================== Error Data Classes ====================

    /**
     * Replicate API error response wrapper.
     * Format: {"error": {...}, "detail": "...", "status": ...}
     */
    data class ReplicateApiErrorWrapper(
        val error: ReplicateApiError?,
        val detail: String? = null,
        val status: Int? = null
    )

    /**
     * Individual Replicate API error details.
     */
    data class ReplicateApiError(
        val message: String?,
        val code: String?,
        val detail: String? = null
    )

    // ==================== Custom Exceptions ====================

    sealed class ReplicateException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : ReplicateException(
            "Invalid API key. Please check your Replicate API token in settings."
        )

        class RateLimitError : ReplicateException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null
        ) : ReplicateException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code
        )

        class ServerError(code: Int) : ReplicateException(
            "Replicate server error (HTTP $code). Please try again later."
        )

        class PredictionFailed(message: String?) : ReplicateException(
            "Prediction failed: ${message ?: "Unknown error"}"
        )

        class PredictionCanceled : ReplicateException(
            "Prediction was canceled by the server."
        )

        class Timeout : ReplicateException(
            "Prediction timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : ReplicateException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null,
            cause: Throwable? = null
        ) : ReplicateException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            cause = cause,
            errorCode = apiErrorCode
        )
    }
}

/**
 * Output from a Replicate prediction.
 */
data class ReplicateOutput(
    val output: Any?,
    val predictionId: String,
    val model: String,
    val version: String,
    val predictTimeSeconds: Double?
) {
    /**
     * Gets output as a list of strings (common for image generation).
     */
    fun asStringList(): List<String> {
        return when (output) {
            is List<*> -> output.filterIsInstance<String>()
            is String -> listOf(output)
            else -> emptyList()
        }
    }

    /**
     * Gets output as a single string.
     */
    fun asString(): String {
        return when (output) {
            is String -> output
            is List<*> -> output.filterIsInstance<String>().firstOrNull() ?: ""
            else -> output?.toString() ?: ""
        }
    }
}
