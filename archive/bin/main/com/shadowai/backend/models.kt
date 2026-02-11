package com.shadowai.backend

import kotlinx.serialization.Serializable

@Serializable
data class ImageGenerateRequest(
    val prompt: String,
    val model: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val guidanceScale: Double,
    val samplerName: String,
    val loras: List<LoraReference> = emptyList(),
    val webhookUrl: String? = null
)

@Serializable
data class LoraReference(
    val modelName: String,
    val strength: Double
)

@Serializable
data class TaskLaunchedResponse(val taskId: String)

@Serializable
data class TaskStatusResponse(
    val taskId: String,
    val status: TaskStatus,
    val progress: Int,
    val images: List<ImageResource>
)

@Serializable
data class ImageResource(
    val url: String,
    val type: String? = null,
    val ttl: String? = null
)

@Serializable
data class NovitaWebhookPayload(
    val event_type: String,
    val task_id: String,
    val task_status: String,
    val images: List<NovitaWebhookImage> = emptyList()
)

@Serializable
data class NovitaWebhookImage(
    val image_url: String,
    val image_type: String? = null,
    val image_url_ttl: String? = null
)

data class NovitaConfig(
    val apiKey: String,
    val baseUrl: String,
    val webhookSecret: String? = null
)

data class PixAiConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.pixai.art",
    val webhookSecret: String? = null
)

@Serializable
data class PixAiWebhookPayload(
    val event_type: String,
    val task_id: String,
    val task_status: String,
    val images: List<PixAiWebhookImage> = emptyList()
)

@Serializable
data class PixAiWebhookImage(
    val image_url: String,
    val image_type: String? = null,
    val image_url_ttl: String? = null
)

@Serializable
enum class TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
