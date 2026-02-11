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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Adapter for Flux image generation models.
 * Supports both local execution (via ComfyUI/diffusers) and cloud APIs (Replicate, fal.ai, Together AI).
 */
class FluxAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient? = null,
    private val gson: Gson? = null
) : ProviderAdapter {

    private companion object {
        private const val TAG = "FluxAdapter"
        private const val POLL_INTERVAL_MS = 2_000L
        private const val HEALTH_CHECK_TIMEOUT_MS = 5_000L
    }

    /**
     * Execution mode for Flux models.
     */
    enum class ExecutionMode {
        /** Local execution via ComfyUI API or diffusers server */
        LOCAL,
        /** Via Replicate API */
        REPLICATE,
        /** Via fal.ai API */
        FAL_AI,
        /** Via Together AI API */
        TOGETHER_AI
    }

    private var isInitialized = false
    private val executionMode: ExecutionMode = determineExecutionMode()

    override val providerId: ProviderId = ProviderId.FLUX

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            isInitialized = when (executionMode) {
                ExecutionMode.LOCAL -> checkLocalEndpoint()
                else -> validateConfig()
            }
            Log.d(TAG, "Initialized FluxAdapter in $executionMode mode: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            false
        }
    }

    override suspend fun validateConfig(): Boolean {
        return when (executionMode) {
            ExecutionMode.LOCAL -> config.baseUrl.isNotBlank()
            else -> {
                val apiKey = config.resolveApiKey()
                config.baseUrl.isNotBlank() && !apiKey.isNullOrBlank()
            }
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false

        when (executionMode) {
            ExecutionMode.LOCAL -> checkLocalEndpoint()
            else -> true // Cloud APIs assumed available if initialized
        }
    }

    override suspend fun canExecute(transform: Transform): Boolean {
        return when (transform) {
            is Transform.TextToImage -> true
            is Transform.ImageToImage -> true // Flux supports img2img
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
                is Transform.TextToImage -> executeTextToImage(input as String, parameters)
                is Transform.ImageToImage -> executeImageToImage(input, parameters)
                else -> Result.failure(UnsupportedOperationException("Transform not supported: $transform"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(e)
        }
    }

    override fun getPriority(transform: Transform): Int {
        return when (transform) {
            is Transform.TextToImage -> 90 // High priority for image generation
            is Transform.ImageToImage -> 85
            else -> 0
        }
    }

    private fun determineExecutionMode(): ExecutionMode {
        val baseUrl = config.baseUrl.lowercase()
        return when {
            baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") -> ExecutionMode.LOCAL
            baseUrl.contains("replicate") -> ExecutionMode.REPLICATE
            baseUrl.contains("fal.ai") || baseUrl.contains("fal.run") -> ExecutionMode.FAL_AI
            baseUrl.contains("together") -> ExecutionMode.TOGETHER_AI
            else -> ExecutionMode.LOCAL // Default to local
        }
    }

    private suspend fun checkLocalEndpoint(): Boolean = withContext(Dispatchers.IO) {
        if (httpClient == null) return@withContext false

        try {
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/system_stats")
                .get()
                .build()

            // Quick health check with short timeout - appropriate for availability checks
            kotlinx.coroutines.withTimeout(HEALTH_CHECK_TIMEOUT_MS) {
                httpClient.newCall(request).execute().use { it.isSuccessful }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local endpoint check failed: ${e.message}")
            false
        }
    }

    private suspend fun executeTextToImage(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        if (httpClient == null || gson == null) {
            return@withContext Result.failure(IllegalStateException("HTTP client not configured"))
        }

        val width = parameters["width"] as? Int ?: 1024
        val height = parameters["height"] as? Int ?: 1024
        val steps = parameters["steps"] as? Int ?: 20
        val guidanceScale = parameters["guidanceScale"] as? Double ?: 7.5
        val seed = parameters["seed"] as? Long ?: System.currentTimeMillis()
        val negativePrompt = parameters["negativePrompt"] as? String ?: ""

        when (executionMode) {
            ExecutionMode.LOCAL -> executeLocalGeneration(prompt, negativePrompt, width, height, steps, guidanceScale, seed)
            ExecutionMode.REPLICATE -> executeReplicateGeneration(prompt, negativePrompt, width, height, steps, guidanceScale, seed)
            ExecutionMode.FAL_AI -> executeFalAiGeneration(prompt, negativePrompt, width, height, steps, guidanceScale, seed)
            ExecutionMode.TOGETHER_AI -> executeTogetherAiGeneration(prompt, negativePrompt, width, height, steps, guidanceScale, seed)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeImageToImage(
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        Result.failure(
            UnsupportedOperationException(
                "Image-to-image is not implemented for FluxAdapter yet."
            )
        )
    }

    private suspend fun executeLocalGeneration(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Double,
        seed: Long
    ): Result<Any> = withContext(Dispatchers.IO) {
        // ComfyUI-style API request
        val workflowPayload = buildComfyUIWorkflow(prompt, negativePrompt, width, height, steps, guidanceScale, seed)

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/prompt")
            .post(gson!!.toJson(workflowPayload).toRequestBody("application/json".toMediaType()))
            .build()

        val response = withRetryInternal {
            httpClient!!.newCall(request).execute()
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            return@withContext Result.failure(parseErrorResponse(response.code, errorBody))
        }

        val responseBody = response.body?.string() ?: ""
        @Suppress("UNCHECKED_CAST")
        val json = gson.fromJson(responseBody, Map::class.java) as? Map<String, Any>
        val promptId = json?.get("prompt_id") as? String
            ?: return@withContext Result.failure(Exception("No prompt ID returned"))

        // Poll for completion (timeout handled by pollForCompletion via config.timeoutSeconds)
        pollForCompletion(promptId)
    }

    private suspend fun pollForCompletion(promptId: String): Result<Any> = withContext(Dispatchers.IO) {
        val timeout = config.timeoutSeconds * 1000L
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val historyRequest = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/history/$promptId")
                .get()
                .build()

            val historyResponse = withRetryInternal {
                httpClient!!.newCall(historyRequest).execute()
            }
            if (historyResponse.isSuccessful) {
                val historyBody = historyResponse.body?.string() ?: "{}"
                @Suppress("UNCHECKED_CAST")
                val history = gson!!.fromJson(historyBody, Map::class.java) as? Map<String, Any>

                @Suppress("UNCHECKED_CAST")
                val promptHistory = history?.get(promptId) as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val outputs = promptHistory?.get("outputs") as? Map<String, Any>

                if (outputs != null && outputs.isNotEmpty()) {
                    // Extract image URLs from outputs
                    val imageUrls = mutableListOf<String>()
                    outputs.values.forEach { output ->
                        @Suppress("UNCHECKED_CAST")
                        val images = (output as? Map<String, Any>)?.get("images") as? List<Map<String, Any>>
                        images?.forEach { img ->
                            val filename = img["filename"] as? String
                            if (filename != null) {
                                imageUrls.add("${config.baseUrl.trimEnd('/')}/view?filename=$filename")
                            }
                        }
                    }

                    return@withContext Result.success(FluxGenerationResult(
                        imageUrls = imageUrls,
                        prompt = "",
                        modelId = config.modelId ?: "flux-local",
                        parameters = emptyMap()
                    ))
                }
            }
            delay(POLL_INTERVAL_MS)
        }

        Result.failure(FluxException.Timeout())
    }

    private fun buildComfyUIWorkflow(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Double,
        seed: Long
    ): Map<String, Any> {
        // Simplified ComfyUI workflow for Flux
        return mapOf(
            "prompt" to mapOf(
                "3" to mapOf(
                    "class_type" to "KSampler",
                    "inputs" to mapOf(
                        "seed" to seed,
                        "steps" to steps,
                        "cfg" to guidanceScale,
                        "sampler_name" to "euler",
                        "scheduler" to "normal",
                        "denoise" to 1.0
                    )
                ),
                "6" to mapOf(
                    "class_type" to "CLIPTextEncode",
                    "inputs" to mapOf(
                        "text" to prompt
                    )
                ),
                "7" to mapOf(
                    "class_type" to "CLIPTextEncode",
                    "inputs" to mapOf(
                        "text" to negativePrompt
                    )
                ),
                "5" to mapOf(
                    "class_type" to "EmptyLatentImage",
                    "inputs" to mapOf(
                        "width" to width,
                        "height" to height,
                        "batch_size" to 1
                    )
                )
            )
        )
    }

    private suspend fun executeReplicateGeneration(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Double,
        seed: Long
    ): Result<Any> = withContext(Dispatchers.IO) {
        val modelVersion = config.modelId ?: "black-forest-labs/flux-schnell"

        val payload = mapOf(
            "version" to modelVersion,
            "input" to mapOf(
                "prompt" to prompt,
                "negative_prompt" to negativePrompt,
                "width" to width,
                "height" to height,
                "num_inference_steps" to steps,
                "guidance_scale" to guidanceScale,
                "seed" to seed
            )
        )

        val replicateApiKey = config.resolveApiKey()
            ?: return@withContext Result.failure(IllegalStateException("API key required for Replicate"))

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/predictions")
            .header("Authorization", "Token $replicateApiKey")
            .header("Content-Type", "application/json")
            .post(gson!!.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        val response = withRetryInternal {
            httpClient!!.newCall(request).execute()
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            return@withContext Result.failure(parseErrorResponse(response.code, errorBody))
        }

        @Suppress("UNCHECKED_CAST")
        val json = gson.fromJson(response.body?.string(), Map::class.java) as? Map<String, Any>
        val predictionUrl = json?.get("urls") as? Map<*, *>
        val getUrl = predictionUrl?.get("get") as? String
            ?: return@withContext Result.failure(Exception("No prediction URL returned"))

        // Poll for completion (timeout handled by pollReplicatePrediction via config.timeoutSeconds)
        pollReplicatePrediction(getUrl, replicateApiKey)
    }

    private suspend fun pollReplicatePrediction(predictionUrl: String, apiKey: String): Result<Any> = withContext(Dispatchers.IO) {
        val timeout = config.timeoutSeconds * 1000L
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val request = Request.Builder()
                .url(predictionUrl)
                .header("Authorization", "Token $apiKey")
                .get()
                .build()

            val response = withRetryInternal {
                httpClient!!.newCall(request).execute()
            }
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                val json = gson!!.fromJson(response.body?.string(), Map::class.java) as? Map<String, Any>
                val status = json?.get("status") as? String

                when (status) {
                    "succeeded" -> {
                        @Suppress("UNCHECKED_CAST")
                        val output = json["output"]
                        val imageUrls = when (output) {
                            is List<*> -> output.filterIsInstance<String>()
                            is String -> listOf(output)
                            else -> emptyList()
                        }
                        return@withContext Result.success(FluxGenerationResult(
                            imageUrls = imageUrls,
                            prompt = "",
                            modelId = config.modelId ?: "flux-replicate",
                            parameters = emptyMap()
                        ))
                    }
                    "failed" -> {
                        val error = json["error"] as? String ?: "Unknown error"
                        return@withContext Result.failure(FluxException.PredictionFailed(error))
                    }
                    "canceled" -> {
                        return@withContext Result.failure(FluxException.PredictionCanceled())
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }

        Result.failure(FluxException.Timeout())
    }

    private suspend fun executeFalAiGeneration(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Double,
        seed: Long
    ): Result<Any> = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "prompt" to prompt,
            "negative_prompt" to negativePrompt,
            "image_size" to mapOf("width" to width, "height" to height),
            "num_inference_steps" to steps,
            "guidance_scale" to guidanceScale,
            "seed" to seed
        )

        val modelEndpoint = config.modelId ?: "fal-ai/flux/schnell"
        val falApiKey = config.resolveApiKey()
            ?: return@withContext Result.failure(IllegalStateException("API key required for fal.ai"))

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/$modelEndpoint")
            .header("Authorization", "Key $falApiKey")
            .header("Content-Type", "application/json")
            .post(gson!!.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        val response = withRetryInternal {
            httpClient!!.newCall(request).execute()
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            return@withContext Result.failure(parseErrorResponse(response.code, errorBody))
        }

        @Suppress("UNCHECKED_CAST")
        val json = gson.fromJson(response.body?.string(), Map::class.java) as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val images = json?.get("images") as? List<Map<String, Any>>
        val imageUrls = images?.mapNotNull { it["url"] as? String } ?: emptyList()

        Result.success(FluxGenerationResult(
            imageUrls = imageUrls,
            prompt = prompt,
            modelId = modelEndpoint,
            parameters = mapOf("width" to width, "height" to height)
        ))
    }

    private suspend fun executeTogetherAiGeneration(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Double,
        seed: Long
    ): Result<Any> = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "model" to (config.modelId ?: "black-forest-labs/FLUX.1-schnell"),
            "prompt" to prompt,
            "negative_prompt" to negativePrompt,
            "width" to width,
            "height" to height,
            "steps" to steps,
            "seed" to seed
        )

        val togetherApiKey = config.resolveApiKey()
            ?: return@withContext Result.failure(IllegalStateException("API key required for Together AI"))

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/images/generations")
            .header("Authorization", "Bearer $togetherApiKey")
            .header("Content-Type", "application/json")
            .post(gson!!.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        val response = withRetryInternal {
            httpClient!!.newCall(request).execute()
        }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            return@withContext Result.failure(parseErrorResponse(response.code, errorBody))
        }

        @Suppress("UNCHECKED_CAST")
        val json = gson.fromJson(response.body?.string(), Map::class.java) as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val data = json?.get("data") as? List<Map<String, Any>>
        val imageUrls = data?.mapNotNull { it["url"] as? String } ?: emptyList()

        Result.success(FluxGenerationResult(
            imageUrls = imageUrls,
            prompt = prompt,
            modelId = config.modelId ?: "flux-together",
            parameters = mapOf("width" to width, "height" to height)
        ))
    }

    private suspend fun <T> withRetryInternal(
        maxRetries: Int = 3,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delayMs = 1000L

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: Exception("Max retries exceeded")
    }

    data class FluxGenerationResult(
        val imageUrls: List<String>,
        val prompt: String,
        val modelId: String,
        val parameters: Map<String, Any>
    )

    private fun parseErrorResponse(code: Int, body: String?): FluxException {
        return when (code) {
            401 -> FluxException.AuthenticationError()
            429 -> FluxException.RateLimitError()
            400 -> {
                val parsed = parseFluxApiError(body)
                FluxException.BadRequest(parsed?.message, parsed?.code)
            }
            500, 502, 503, 504 -> FluxException.ServerError(code)
            else -> {
                val parsed = parseFluxApiError(body)
                FluxException.UnknownError(code, parsed?.message, parsed?.code)
            }
        }
    }

    /**
     * Parses Flux API error response JSON to extract structured error information.
     * Expected format varies by provider (Replicate, fal.ai, etc.)
     */
    private fun parseFluxApiError(body: String?): FluxApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorResponse = gson!!.fromJson(body, FluxApiErrorWrapper::class.java)
            errorResponse?.error ?: FluxApiError(
                message = errorResponse?.detail ?: body,
                code = errorResponse?.status?.toString()
            )
        } catch (e: Exception) {
            // Fallback: try simple format
            try {
                val simpleError = gson!!.fromJson(body, FluxApiError::class.java)
                simpleError
            } catch (_: Exception) {
                FluxApiError(message = body, code = null)
            }
        }
    }

    // ==================== Error Data Classes ====================

    /**
     * Flux API error response wrapper.
     */
    data class FluxApiErrorWrapper(
        val error: FluxApiError?,
        val detail: String? = null,
        val status: Int? = null,
        val message: String? = null
    )

    /**
     * Individual Flux API error details.
     */
    data class FluxApiError(
        val message: String?,
        val code: String?
    )

    // ==================== Custom Exceptions ====================

    sealed class FluxException(
        message: String,
        cause: Throwable? = null,
        val errorCode: String? = null
    ) : Exception(message, cause) {

        class AuthenticationError : FluxException(
            "Invalid API key. Please check your Flux provider API key in settings."
        )

        class RateLimitError : FluxException(
            "Rate limit exceeded. Please wait a moment and try again."
        )

        class BadRequest(
            message: String?,
            code: String? = null
        ) : FluxException(
            message = "Bad request: ${message ?: "Unknown error"}",
            errorCode = code
        )

        class ServerError(code: Int) : FluxException(
            "Flux server error (HTTP $code). Please try again later."
        )

        class PredictionFailed(message: String?) : FluxException(
            "Prediction failed: ${message ?: "Unknown error"}"
        )

        class PredictionCanceled : FluxException(
            "Prediction was canceled by the server."
        )

        class Timeout : FluxException(
            "Generation timed out. The model may be too busy or the request too large."
        )

        class NetworkError(cause: IOException) : FluxException(
            "Network error: ${cause.message}",
            cause
        )

        class UnknownError(
            code: Int,
            message: String?,
            apiErrorCode: String? = null
        ) : FluxException(
            message = "Unknown error (HTTP $code): ${message ?: "No details"}",
            errorCode = apiErrorCode
        )
    }
}
