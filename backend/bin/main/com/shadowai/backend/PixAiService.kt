package com.shadowai.backend

import com.shadowai.backend.cache.TaskCache
import com.shadowai.backend.cache.TaskInfo
import kotlinx.coroutines.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PixAiService(
    private val client: HttpClient,
    private val config: PixAiConfig,
    private val cache: TaskCache,
    private val json: Json,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    suspend fun createImageTask(request: ImageGenerateRequest): TaskInfo {
        val payload = PixAiSubmitPayload(
            modelId = request.model,
            prompt = request.prompt,
            negativePrompt = "",
            width = request.width,
            height = request.height,
            samplingSteps = request.steps,
            cfgScale = request.guidanceScale,
            samplingMethod = request.samplerName,
            batchSize = 1,
            loras = request.loras.map { PixAiLora(it.modelName, it.strength) }
        )
        val submitResponse: PixAiSubmitResponse = client.post("${config.baseUrl.trimEnd('/')}/v1/generate") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                append(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            setBody(payload)
        }.body()
        val taskId = submitResponse.taskId
            ?: throw IllegalStateException("PixAI submit response missing taskId")
        val entry = cache.upsert(taskId) {
            TaskInfo(taskId = taskId, status = TaskStatus.PENDING)
        }
        scope.launch {
            pollTaskResult(taskId)
        }
        return entry
    }

    fun processWebhook(payload: PixAiWebhookPayload): TaskInfo? {
        if (!cache.hasTask(payload.task_id)) return null
        val images = payload.images.map {
            ImageResource(it.image_url, it.image_type, it.image_url_ttl)
        }
        cache.updateStatus(
            payload.task_id,
            statusFromString(payload.task_status),
            progress = if (images.isNotEmpty()) 100 else 0,
            images = images
        )
        return cache.get(payload.task_id)
    }

    private suspend fun pollTaskResult(taskId: String) {
        var delayMs = 1000L
        val maxDelay = 10_000L
        val maxRetries = 60 // Maximum 60 retries (~2-10 minutes depending on delays)
        val maxTimeoutMs = 10 * 60 * 1000L // 10 minute absolute timeout
        val startTime = System.currentTimeMillis()
        var retryCount = 0
        
        while (retryCount < maxRetries) {
            // Check absolute timeout
            if (System.currentTimeMillis() - startTime > maxTimeoutMs) {
                cache.updateStatus(taskId, TaskStatus.FAILED, progress = 0)
                break
            }
            
            retryCount++
            
            val response = try {
                client.get("${config.baseUrl.trimEnd('/')}/v1/tasks/${taskId}") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    }
                }
            } catch (e: Exception) {
                // Network error - continue retrying
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelay)
                continue
            }
            
            when {
                response.status == HttpStatusCode.TooManyRequests -> {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(maxDelay)
                    continue
                }
                response.status.isSuccess() -> {
                    val payload = response.body<PixAiTaskResponse>()
                    val status = statusFromString(payload.status)
                    val images = payload.images?.mapNotNull { it.toResource() }.orEmpty()
                    cache.updateStatus(taskId, status, progress = payload.progress ?: 0, images = images)
                    if (status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED) {
                        return // Exit successfully
                    }
                }
                else -> {
                    cache.updateStatus(taskId, TaskStatus.FAILED, progress = 0)
                    return // Exit on error
                }
            }
            delay(2000)
        }
        
        // Max retries exceeded
        cache.updateStatus(taskId, TaskStatus.FAILED, progress = 0)
    }

    private fun statusFromString(value: String?): TaskStatus {
        return when (value?.lowercase()) {
            "success", "succeed", "completed", "done" -> TaskStatus.SUCCEEDED
            "failed", "fail", "error" -> TaskStatus.FAILED
            "running", "processing", "in_progress" -> TaskStatus.RUNNING
            else -> TaskStatus.PENDING
        }
    }

    @Serializable
    private data class PixAiSubmitPayload(
        val modelId: String,
        val prompt: String,
        val negativePrompt: String? = null,
        val width: Int,
        val height: Int,
        val samplingSteps: Int,
        val cfgScale: Double,
        val samplingMethod: String,
        val batchSize: Int,
        val loras: List<PixAiLora> = emptyList(),
        val vaeModelId: String? = null,
        val seed: Long? = null
    )

    @Serializable
    private data class PixAiLora(
        val loraId: String,
        val strength: Double
    )

    @Serializable
    private data class PixAiSubmitResponse(
        val taskId: String?,
        val status: String?
    )

    @Serializable
    private data class PixAiTaskResponse(
        val taskId: String?,
        val status: String?,
        val progress: Int?,
        val images: List<PixAiTaskImage>? = null
    )

    @Serializable
    private data class PixAiTaskImage(
        val url: String?,
        val type: String? = null,
        val ttl: String? = null
    ) {
        fun toResource(): ImageResource? {
            val imageUrl = url ?: return null
            return ImageResource(imageUrl, type, ttl)
        }
    }
}
