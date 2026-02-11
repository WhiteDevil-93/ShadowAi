package com.shadowai.app.admin

import com.shadowai.app.models.ModelId
import com.shadowai.core.ProviderId
import com.shadowai.app.routing.RoutingPolicy
import com.shadowai.app.tasks.Task

/**
 * Defines the contract for the privileged administrative control surface.
 */
interface AdminContract {

    suspend fun enableModel(modelId: ModelId)
    suspend fun disableModel(modelId: ModelId)
    suspend fun setRoutingPolicy(policy: RoutingPolicy)
    fun getExecutionHistory(): List<Task>
    fun isModelEnabled(modelId: ModelId): Boolean

    suspend fun setCloudApiKey(key: String)
    suspend fun getCloudApiKey(): String?

    suspend fun setCustomBaseUrl(url: String)
    fun getCustomBaseUrl(): String?

    suspend fun setCloudProvider(provider: String)
    suspend fun getCloudProvider(): String

    suspend fun isVoiceEnabled(): Boolean
    suspend fun setVoiceEnabled(enabled: Boolean)

    suspend fun isMemoryEnabled(): Boolean
    suspend fun setMemoryEnabled(enabled: Boolean)

    suspend fun setModelName(model: String)
    suspend fun getModelName(): String

    suspend fun getGenerationSettings(): GenerationSettings
    suspend fun saveGenerationSettings(settings: GenerationSettings)

    suspend fun setActiveProvider(id: ProviderId)
    suspend fun getActiveProvider(): ProviderId?

    suspend fun setActiveProviderBaseUrl(url: String)
    suspend fun getActiveProviderBaseUrl(): String?

    suspend fun setActiveProviderApiKey(key: String?)
    suspend fun getActiveProviderApiKey(): String?

    // Message/Memory Operations
    suspend fun getAllMessages(): List<com.shadowai.app.ui.ChatMessage>
    suspend fun deleteMessagesFrom(timestamp: Long)
    suspend fun saveMemory(key: String, value: String, layer: String, confidence: Float)
    suspend fun getMemoryByLayer(layer: String): List<com.shadowai.app.db.MemoryEntity>
    suspend fun deleteLowConfidenceMemory(threshold: Float)
    suspend fun recordTask(task: Task)
    suspend fun updateTrustScore(delta: Float)

    // Trust verification
    suspend fun isBackendTrusted(): Boolean

    // Model paths (used by various components)
    suspend fun getSdModelPath(): String
    suspend fun setSdModelPath(path: String)
    suspend fun getPreferredLlamaModel(): String?
    suspend fun setPreferredLlamaModel(modelName: String)
    suspend fun getLocalTextModelPath(): String
    suspend fun setLocalTextModelPath(path: String)

    // PixAi Settings
    suspend fun getPixAiSettings(): com.shadowai.app.functions.PixAiSettings
    suspend fun savePixAiSettings(settings: com.shadowai.app.functions.PixAiSettings)
}

data class GenerationSettings(
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val contentFilterEnabled: Boolean = true,
    val repetitionPenalty: Double = 1.0,
    val presencePenalty: Double = 0.0,
    val mirostatEnabled: Boolean = false,
    val mirostatTau: Double = 5.0,
    val mirostatEta: Double = 0.1,
    val filterHate: Boolean = true,
    val filterViolence: Boolean = true,
    val filterAdult: Boolean = true,
    val filterSelfHarm: Boolean = true,
    val blocklist: List<String> = emptyList(),
    val allowlist: List<String> = emptyList()
)
