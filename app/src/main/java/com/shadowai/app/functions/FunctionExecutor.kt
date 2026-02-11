package com.shadowai.app.functions

import android.util.Log
import com.google.gson.Gson
import com.shadowai.app.functions.PixAiSettings
import com.shadowai.app.functions.ImageGenParams
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Executor for various AI functions.
 */
@Singleton
class FunctionExecutor @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {

    suspend fun runNovitaImage(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        params: ImageGenParams
    ): Result<List<ImageGenResult>> {
        return try {
            val json = gson.toJson(params)
            val request = Request.Builder()
                .url("$baseUrl/v1/images/generations")
                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    return@use Result.failure(IllegalStateException("Novita image generation failed: ${response.code} ${response.message}: $errorBody"))
                }
                val responseBody = response.body?.string() ?: ""
                val result = gson.fromJson(responseBody, NovitaImageResponse::class.java)
                Result.success(result.data.map { ImageGenResult(it.url) })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Top-level declaration
    suspend fun runStableDiffusion(modelPath: String, // This parameter is now ignored since we're using the backend service
                                    settings: PixAiSettings): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(modelPath).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    return@withContext Result.failure(IllegalStateException("Stable diffusion failed: ${response.code} ${response.message}: $errBody"))
                }
                val result = response.body?.string() ?: ""
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadImageBytes(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    return@withContext Result.failure(IllegalStateException("Image download failed: ${response.code} ${response.message}: $errBody"))
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Image download returned empty body"))
                }
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Inner data classes
    data class ImageGenResult(val url: String)
    data class NovitaImageResponse(val data: List<NovitaImageData>)
    data class NovitaImageData(val url: String)
}
