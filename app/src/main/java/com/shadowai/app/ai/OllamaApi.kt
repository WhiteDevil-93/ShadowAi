package com.shadowai.app.ai

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Ollama API interface for local model inference.
 * 
 * IMPROVEMENTS:
 * - Added @Keep annotations for R8/ProGuard compatibility
 * - Nullable fields have sensible defaults
 * - Response uses non-nullable patterns where possible
 */
interface OllamaApi {

    @POST("api/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(
        @Body request: OllamaChatRequest
    ): Response<OllamaChatResponse>
}

/**
 * Request body for Ollama chat API.
 * 
 * @param model The model name to use
 * @param messages Conversation messages
 * @param stream Whether to stream the response
 * @param options Additional model options (temperature, num_predict, etc.)
 */
@Keep
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: Map<String, Any>? = null
)

/**
 * Message in Ollama conversation format.
 * 
 * @param role The role (system, user, assistant)
 * @param content The message content
 */
@Keep
data class OllamaMessage(
    val role: String,
    val content: String
)

/**
 * Response from Ollama chat API.
 * 
 * @param message The assistant's message (null if using legacy response format)
 * @param response Legacy response text (null if using message format)
 * @param done Whether the response is complete
 */
@Keep
data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    val response: String? = null,
    @SerializedName("done") val done: Boolean = false
)
