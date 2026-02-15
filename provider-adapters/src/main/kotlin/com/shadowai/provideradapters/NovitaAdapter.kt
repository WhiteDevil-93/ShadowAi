package com.shadowai.provideradapters

import android.util.Base64
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
import okhttp3.Response
import java.io.File
import java.io.IOException

import com.shadowai.provideradapters.NovitaTextToImageRequest
import com.shadowai.provideradapters.NovitaImageToImageRequest
import com.shadowai.provideradapters.NovitaAsyncResponse
import com.shadowai.provideradapters.NovitaTaskResult
import com.shadowai.provideradapters.NovitaImageResult
import com.shadowai.provideradapters.NovitaException
import com.shadowai.provideradapters.ImageGenerationParameters.TextToImageParameters
import com.shadowai.provideradapters.ImageGenerationParameters.ImageToImageParameters
import com.shadowai.provideradapters.NovitaApiError
import com.shadowai.provideradapters.NovitaApiErrorWrapper
import com.shadowai.provideradapters.NovitaSimpleError


/**
 * Adapter for Novita AI API.
 * Supports text-to-image, image-to-image, and inpainting.
 * Novita provides various diffusion models through a unified API.
 *
 * API Docs: https://novita.ai/docs
 */
class NovitaAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : ProviderAdapter {

    private companion object {
        private const val TAG = "NovitaAdapter"
        private const val POLL_INTERVAL_MS = 1000L
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val DEFAULT_MODEL = "sd_xl_base_1.0"
    }

    private var isInitialized = false

    override val providerId: ProviderId = ProviderId.NOVITA

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
               !config.resolveApiKey().isNullOrBlank()
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        // H-10 FIX: Lightweight check to avoid network overhead on every request.
        return@withContext isInitialized
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
            is Transform.TextToImage -> 75
            is Transform.ImageToImage -> 75
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
                is Transform.TextToImage -> {
                    if (input !is String) {
                        return@withContext Result.failure(IllegalArgumentException("Input for TextToImage must be a String prompt."))
                    }
                    executeTextToImage(input, parameters)
                }
                is Transform.ImageToImage -> executeImageToImage(input, parameters)
                else -> Result.failure(UnsupportedOperationException(
                    "Transform ${transform::class.simpleName} not supported by Novita"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            Result.failure(mapToDomainError(e))
        }
    }

    // ==================== Private Implementation Methods ====================

    private suspend fun executeTextToImage(
        prompt: String,
        parameters: Map<String, Any>
    ): Result<NovitaImageResult> {
        val apiKey = config.resolveApiKey()
            ?: return Result.failure(NovitaException.AuthenticationError())

        val modelId = config.modelId ?: DEFAULT_MODEL
        val params = try {
            TextToImageParameters.fromMap(parameters)
        } catch (e: IllegalArgumentException) {
            return Result.failure(NovitaException.BadRequest(e.message))
        }

        val requestBody = NovitaTextToImageRequest(
            modelName = modelId,
            prompt = prompt,
            negativePrompt = params.negativePrompt,
            width = params.width,
            height = params.height,
            steps = params.steps,
            guidanceScale = params.guidanceScale,
            seed = params.seed,
            sampler = params.sampler,
            batchSize = params.batchSize
        )

        val request = Request.Builder()
            .url("${getBaseUrl()}/v3/async/txt2img")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequestAndPoll(request)
    }

    private suspend fun executeImageToImage(
        input: Any,
        parameters: Map<String, Any>
    ): Result<NovitaImageResult> {
        val apiKey = config.resolveApiKey()
            ?: return Result.failure(NovitaException.AuthenticationError())

        val modelId = config.modelId ?: DEFAULT_MODEL
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
            return Result.failure(NovitaException.BadRequest(e.message))
        }

        val base64Data = when (imageData) {
            is File -> encodeImageToBase64Raw(imageData.readBytes())
            is ByteArray -> encodeImageToBase64Raw(imageData)
            is String -> if (imageData.startsWith("data:image/")) imageData.substringAfter(',') else imageData
            else -> return Result.failure(
                NovitaException.BadRequest("No valid image provided. Expected File, ByteArray, or base64 string.")
            )
        }

        val requestBody = NovitaImageToImageRequest(
            modelName = modelId,
            prompt = prompt,
            negativePrompt = params.negativePrompt,
            image = base64Data,
            width = params.width,
            height = params.height,
            steps = params.steps,
            guidanceScale = params.guidanceScale,
            strength = params.strength,
            seed = params.seed,
            sampler = params.sampler
        )

        val request = Request.Builder()
            .url("${getBaseUrl()}/v3/async/img2img")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequestAndPoll(request)
    }

    private suspend fun executeRequestAndPoll(request: Request): Result<NovitaImageResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = withRetryInternal { httpClient.newCall(request).execute() }

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string()
                        return@withContext Result.failure(parseErrorResponse(resp.code, errorBody))
                    }

                    val responseBody = resp.body?.string()
                        ?: return@withContext Result.failure(NovitaException.EmptyResponse())

                    val parsed = gson.fromJson(responseBody, NovitaAsyncResponse::class.java)
                    val taskId = parsed.taskId
                        ?: return@withContext Result.failure(NovitaException.EmptyContent())

                    pollForTaskCompletion(taskId)
                }
            } catch (e: Exception) {
                Result.failure(mapToDomainError(e))
            }
        }
    }

    private suspend fun pollForTaskCompletion(taskId: String): Result<NovitaImageResult> {
        val timeout = config.timeoutSeconds * 1000L
        val startTime = System.currentTimeMillis()
        val pollApiKey = config.resolveApiKey()
            ?: return Result.failure(NovitaException.AuthenticationError())

        while (System.currentTimeMillis() - startTime < timeout) {
            val request = Request.Builder()
                .url("${getBaseUrl()}/v3/async/task-result?taskId=$taskId")
                .header("Authorization", "Bearer $pollApiKey")
                .get()
                .build()

            try {
                val response = withRetryInternal { httpClient.newCall(request).execute() }

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val result = gson.fromJson(body, NovitaTaskResult::class.java)

                    when (result.status) {
                        "SUCCESS" -> {
                            val images = result.images ?: emptyList()
                            return Result.success(NovitaImageResult(
                                imageUrls = images,
                                imageData = emptyList(),
                                modelId = config.modelId ?: DEFAULT_MODEL,
                                parameters = emptyMap()
                            ))
                        }
                        "FAILED", "ERROR" -> {
                            return Result.failure(
                                NovitaException.UnknownError(-1, result.reason ?: "Task failed")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Polling attempt failed", e)
            }

            delay(POLL_INTERVAL_MS)
        }

        return Result.failure(NovitaException.Timeout())
    }

    private fun getBaseUrl(): String {
        return config.baseUrl.trimEnd('/')
    }

    private fun encodeImageToBase64Raw(imageBytes: ByteArray): String {
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    private suspend fun withRetryInternal(
        maxRetries: Int = MAX_RETRIES,
        block: suspend () -> Response
    ): Response {
        var lastException: Exception? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(maxRetries + 1) { attempt ->
            try {
                val response = block()
                if (response.isSuccessful || response.code !in 500..599) {
                    return response
                }
                lastException = NovitaException.ServerError(response.code)
            } catch (e: IOException) {
                lastException = e
            }

            if (attempt < maxRetries) {
                delay(delayMs)
                delayMs *= 2
            }
        }

        throw lastException ?: NovitaException.UnknownError(-1, "Max retries exceeded")
    }

    private fun parseErrorResponse(code: Int, body: String?): NovitaException {
        return when (code) {
            401 -> NovitaException.AuthenticationError()
            429 -> NovitaException.RateLimitError()
            400 -> {
                val parsedError = parseNovitaApiError(body)
                NovitaException.BadRequest(parsedError?.message ?: "Unknown error")
            }
            in 500..599 -> NovitaException.ServerError(code)
            else -> {
                val parsedError = parseNovitaApiError(body)
                NovitaException.UnknownError(code, parsedError?.message)
            }
        }
    }

    private fun parseNovitaApiError(body: String?): NovitaApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            val errorWrapper = gson.fromJson(body, NovitaApiErrorWrapper::class.java)
            errorWrapper?.error
        } catch (e: Exception) {
            try {
                val simpleError = gson.fromJson(body, NovitaSimpleError::class.java)
                NovitaApiError(
                    message = simpleError.message ?: body,
                    code = simpleError.code
                )
            } catch (_: Exception) {
                NovitaApiError(message = body, code = null)
            }
        }
    }

    /**
     * M-2 FIX: Preserve original exception context in error mapping.
     * The original exception is now passed as the cause for better debugging.
     */
    private fun mapToDomainError(e: Exception): NovitaException {
        return when (e) {
            is IOException -> NovitaException.NetworkError(e)
            is NovitaException -> e
            // M-2 FIX: Pass original exception as cause instead of just message
            else -> NovitaException.UnknownError(-1, e.message, cause = e)
        }
    }
}
