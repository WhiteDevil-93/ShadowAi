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
) {
    companion object {
        // Valid ranges for settings that must be between 0.0 and 1.0
        const val TEMPERATURE_MIN = 0.0
        const val TEMPERATURE_MAX = 1.0
        const val TOP_P_MIN = 0.0
        const val TOP_P_MAX = 1.0
        const val PRESENCE_PENALTY_MIN = 0.0
        const val PRESENCE_PENALTY_MAX = 1.0
        
        // Repetition penalty typically ranges from 1.0 (no penalty) to 2.0
        const val REPETITION_PENALTY_MIN = 1.0
        const val REPETITION_PENALTY_MAX = 2.0
    }

    /**
     * Validates all settings are within their defined bounds.
     * @return ValidationResult indicating if the settings are valid and any error messages.
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (temperature !in TEMPERATURE_MIN..TEMPERATURE_MAX) {
            errors.add("Temperature must be between $TEMPERATURE_MIN and $TEMPERATURE_MAX")
        }
        
        if (topP !in TOP_P_MIN..TOP_P_MAX) {
            errors.add("TopP must be between $TOP_P_MIN and $TOP_P_MAX")
        }
        
        if (presencePenalty !in PRESENCE_PENALTY_MIN..PRESENCE_PENALTY_MAX) {
            errors.add("Presence penalty must be between $PRESENCE_PENALTY_MIN and $PRESENCE_PENALTY_MAX")
        }
        
        if (repetitionPenalty !in REPETITION_PENALTY_MIN..REPETITION_PENALTY_MAX) {
            errors.add("Repetition penalty must be between $REPETITION_PENALTY_MIN and $REPETITION_PENALTY_MAX")
        }
        
        if (maxTokens < 1) {
            errors.add("Max tokens must be at least 1")
        }
        
        if (topK < 1) {
            errors.add("TopK must be at least 1")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Creates a new GenerationSettings with all values clamped to their valid ranges.
     */
    fun clamped(): GenerationSettings {
        return copy(
            temperature = temperature.coerceIn(TEMPERATURE_MIN, TEMPERATURE_MAX),
            topP = topP.coerceIn(TOP_P_MIN, TOP_P_MAX),
            presencePenalty = presencePenalty.coerceIn(PRESENCE_PENALTY_MIN, PRESENCE_PENALTY_MAX),
            repetitionPenalty = repetitionPenalty.coerceIn(REPETITION_PENALTY_MIN, REPETITION_PENALTY_MAX),
            maxTokens = maxTokens.coerceAtLeast(1),
            topK = topK.coerceAtLeast(1)
        )
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
        
        val isValid: Boolean get() = this is Valid
    }
}
