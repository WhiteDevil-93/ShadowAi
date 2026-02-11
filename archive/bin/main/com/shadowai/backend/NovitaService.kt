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

class NovitaService(
    private val client: HttpClient,
    private val config: NovitaConfig,
    private val cache: TaskCache,
    private val json: Json,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    suspend fun createImageTask(request: ImageGenerateRequest): TaskInfo {
        val payload = NovitaSubmitPayload(
            extra = request.webhookUrl?.let { NovitaExtra(NovitaWebhook(it)) },
            request = NovitaImagePayload(
                model_name = request.model,
                prompt = request.prompt,
                width = request.width,
                height = request.height,
                steps = request.steps,
                guidance_scale = request.guidanceScale,
                sampler_name = request.samplerName,
                image_num = 1,
                loras = request.loras.map { NovitaLora(it.modelName, it.strength) }
            )
        )
        val submitResponse: NovitaSubmitResponse = client.post("${config.baseUrl.trimEnd('/')}/v3/async/txt2img") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                append(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            setBody(payload)
        }.body()
        val taskId = submitResponse.task_id
            ?: throw IllegalStateException("Novita submit response missing task_id")
        val entry = cache.upsert(taskId) {
            TaskInfo(taskId = taskId, status = TaskStatus.PENDING)
        }
        scope.launch {
            pollTaskResult(taskId)
        }
        return entry
    }

    fun processWebhook(payload: NovitaWebhookPayload): TaskInfo? {
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
        val maxRetries = 60 // Maximum 60 retries (~1-10 minutes depending on delays)
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
                client.get("${config.baseUrl.trimEnd('/')}/v3/async/task-result") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    }
                    parameter("task_id", taskId)
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
                    val payload = response.body<NovitaTaskResponse>()
                    val status = statusFromString(payload.task?.status)
                    val images = payload.images?.mapNotNull { it.toResource() }.orEmpty()
                    cache.updateStatus(taskId, status, progress = if (images.isNotEmpty()) 100 else 0, images = images)
                    if (status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED) {
                        return // Exit successfully
                    }
                }
                else -> {
                    cache.updateStatus(taskId, TaskStatus.FAILED, progress = 0)
                    return // Exit on error
                }
            }
            delay(1000)
        }
        
        // Max retries exceeded
        cache.updateStatus(taskId, TaskStatus.FAILED, progress = 0)
    }

    private fun statusFromString(value: String?): TaskStatus {
        return when (value?.lowercase()) {
            "success", "succeed", "completed" -> TaskStatus.SUCCEEDED
            "failed", "fail", "error" -> TaskStatus.FAILED
            "running", "processing" -> TaskStatus.RUNNING
            else -> TaskStatus.PENDING
        }
    }

    @Serializable
    private data class NovitaSubmitPayload(
        val extra: NovitaExtra? = null,
        val request: NovitaImagePayload
    )

    @Serializable
    private data class NovitaImagePayload(
        val model_name: String,
        val prompt: String,
        val negative_prompt: String? = null,
        val width: Int,
        val height: Int,
        val image_num: Int,
        val steps: Int,
        val guidance_scale: Double,
        val sampler_name: String,
        val loras: List<NovitaLora>
    )

    @Serializable
    private data class NovitaLora(val model_name: String, val strength: Double)

    @Serializable
    private data class NovitaExtra(val webhook: NovitaWebhook? = null)

    @Serializable
    private data class NovitaWebhook(val url: String)

    @Serializable
    private data class NovitaSubmitResponse(val task_id: String?)

    @Serializable
    private data class NovitaTaskResponse(
        val task: NovitaTaskInfo? = null,
        val images: List<NovitaTaskImage>? = null
    )

    @Serializable
    private data class NovitaTaskInfo(val status: String? = null)

    @Serializable
    private data class NovitaTaskImage(
        val image_url: String?,
        val image_type: String? = null,
        val image_url_ttl: String? = null
    ) {
        fun toResource(): ImageResource? {
            val url = image_url ?: return null
            return ImageResource(url, image_type, image_url_ttl)
        }
    }

}
