package com.shadowai.modelcatalog

import com.shadowai.core.Capability
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.PerformanceProfile
import com.shadowai.core.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for managing AI models across all providers.
 * Provides centralized model discovery, deduplication, and caching.
 *
 * ## Semantic Deduplication
 * Models with the same semantic ID (e.g., "gpt-4" from different providers)
 * are tracked together for intelligent deduplication.
 *
 * ## Capability-Based Selection
 * Models can be queried by their capabilities to find the best model
 * for a specific task type.
 */
class ModelRegistry {
    // Primary storage: modelId -> ModelDescriptor
    private val models = ConcurrentHashMap<String, ModelDescriptor>()

    // Reverse index: semanticId -> List<modelIds>
    private val semanticIndex = ConcurrentHashMap<String, MutableSet<String>>()

    // Reverse index: providerId -> List<modelIds>
    private val providerModels = ConcurrentHashMap<ProviderId, MutableList<String>>()

    // Capability index: Capability -> List<modelIds>
    private val capabilityIndex = ConcurrentHashMap<Capability, MutableSet<String>>()

    private val indexLock = Any()

    private val _modelsFlow = MutableStateFlow<List<ModelDescriptor>>(emptyList())
    val modelsFlow: StateFlow<List<ModelDescriptor>> = _modelsFlow.asStateFlow()

    private val _capabilitiesFlow = MutableStateFlow<Set<Capability>>(emptySet())
    val availableCapabilities: StateFlow<Set<Capability>> = _capabilitiesFlow.asStateFlow()

    /**
     * Registers a model in the registry.
     *
     * If a model with the same ID already exists, it will be replaced.
     * Semantic ID is extracted from the model ID or metadata for deduplication.
     *
     * @param model The model descriptor to register
     */
    fun registerModel(model: ModelDescriptor) {
        val semanticId = extractSemanticId(model)

        // Remove old model from indexes if replacing
        models[model.id]?.let { oldModel ->
            removeFromIndexes(oldModel)
        }

        // Store model
        models[model.id] = model

        // Add to indexes
        synchronized(indexLock) {
            semanticIndex.getOrPut(semanticId) { mutableSetOf() }.add(model.id)
            providerModels.getOrPut(model.providerId) { mutableListOf() }.add(model.id)

            // Add to capability index
            model.capabilities.forEach { capability ->
                capabilityIndex.getOrPut(capability) { mutableSetOf() }.add(model.id)
            }
        }

        updateModelsFlow()
        updateCapabilitiesFlow()
    }

    /**
     * Registers multiple models at once.
     */
    fun registerModels(newModels: List<ModelDescriptor>) {
        newModels.forEach { registerModel(it) }
    }

    /**
     * Unregisters a model by its ID.
     */
    fun unregisterModel(modelId: String) {
        models.remove(modelId)?.let { removed ->
            removeFromIndexes(removed)
            updateModelsFlow()
            updateCapabilitiesFlow()
        }
    }

    /**
     * Gets a model by its ID.
     */
    fun getModel(modelId: String): ModelDescriptor? = models[modelId]

    /**
     * Gets all models for a specific provider.
     */
    fun getModelsForProvider(providerId: ProviderId): List<ModelDescriptor> {
        return providerModels[providerId]?.mapNotNull { models[it] } ?: emptyList()
    }

    /**
     * Gets all registered models.
     */
    fun getAllModels(): List<ModelDescriptor> = models.values.toList()

    /**
     * Finds models that support a specific capability.
     *
     * @param capability The capability to search for
     * @return List of models with the specified capability
     */
    fun findModelsByCapability(capability: Capability): List<ModelDescriptor> {
        return capabilityIndex[capability]?.mapNotNull { models[it] } ?: emptyList()
    }

    /**
     * Finds models that have ALL of the specified capabilities.
     *
     * @param required The set of required capabilities
     * @return List of models with all required capabilities
     */
    fun findModelsWithAllCapabilities(required: Set<Capability>): List<ModelDescriptor> {
        if (required.isEmpty()) return getAllModels()

        // Start with models for first capability, then filter
        val firstCap = required.first()
        val candidates = findModelsByCapability(firstCap)

        return candidates.filter { model ->
            required.all { model.hasCapability(it) }
        }
    }

    /**
     * Finds the best model for a set of required capabilities.
     *
     * Scoring considers:
     * - Number of matching capabilities (most important)
     * - Preferred provider (if specified)
     * - Performance profile scores
     *
     * @param required The required capabilities
     * @param preferredProvider Optional preferred provider
     * @return The best matching model, or null if none match
     */
    fun findBestModelForCapabilities(
        required: Set<Capability>,
        preferredProvider: ProviderId? = null
    ): ModelDescriptor? {
        val candidates = findModelsWithAllCapabilities(required)
        if (candidates.isEmpty()) return null

        return candidates.maxWithOrNull(compareBy(
            // Prefer preferred provider
            { if (preferredProvider != null && it.providerId == preferredProvider) 1 else 0 },
            // Prefer models with more total capabilities (more versatile)
            { it.capabilities.size },
            // Prefer lower latency for fast tasks
            { it.performanceProfile.latencyScore }
        ))
    }

    /**
     * Finds models by their semantic ID.
     *
     * Semantic ID identifies the same logical model across different providers
     * (e.g., "gpt-4" from OpenAI vs OpenRouter).
     *
     * @param semanticId The semantic identifier
     * @return List of models with this semantic ID
     */
    fun findModelsBySemanticId(semanticId: String): List<ModelDescriptor> {
        return semanticIndex[semanticId]?.mapNotNull { models[it] } ?: emptyList()
    }

    /**
     * Checks if a model with the given semantic ID is already registered.
     *
     * @param semanticId The semantic identifier to check
     * @return True if at least one model with this semantic ID exists
     */
    fun hasDuplicate(semanticId: String): Boolean {
        return semanticIndex[semanticId]?.isNotEmpty() ?: false
    }

    /**
     * Gets all semantic IDs currently registered.
     */
    fun getAllSemanticIds(): Set<String> {
        return semanticIndex.keys.toSet()
    }

    /**
     * Gets the count of unique semantic IDs.
     */
    fun getSemanticIdCount(): Int = semanticIndex.size

    /**
     * Finds models that support a specific transform.
     */
    fun findModelsForTransform(transform: Class<*>): List<ModelDescriptor> {
        return models.values.filter { model ->
            model.supportedTransforms.any { it::class.java == transform }
        }
    }

    /**
     * Searches models by name or ID.
     */
    fun searchModels(query: String): List<ModelDescriptor> {
        val lowerQuery = query.lowercase()
        return models.values.filter { model ->
            model.id.lowercase().contains(lowerQuery) ||
            model.name.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Gets all distinct capabilities across all registered models.
     */
    fun getAllCapabilities(): Set<Capability> {
        return capabilityIndex.keys.toSet()
    }

    /**
     * Gets the coverage count for a specific capability
     * (how many models support this capability).
     */
    fun getCapabilityCoverage(capability: Capability): Int {
        return capabilityIndex[capability]?.size ?: 0
    }

    /**
     * Clears all registered models.
     */
    fun clear() {
        models.clear()
        semanticIndex.clear()
        providerModels.clear()
        capabilityIndex.clear()
        updateModelsFlow()
        updateCapabilitiesFlow()
    }

    /**
     * Returns the count of registered models.
     */
    fun getModelCount(): Int = models.size

    /**
     * Checks if the registry has any models.
     */
    fun isEmpty(): Boolean = models.isEmpty()

    private fun removeFromIndexes(model: ModelDescriptor) {
        val semanticId = extractSemanticId(model)

        synchronized(indexLock) {
            semanticIndex[semanticId]?.remove(model.id)
            if (semanticIndex[semanticId]?.isEmpty() == true) {
                semanticIndex.remove(semanticId)
            }

            providerModels[model.providerId]?.remove(model.id)

            model.capabilities.forEach { capability ->
                capabilityIndex[capability]?.remove(model.id)
                if (capabilityIndex[capability]?.isEmpty() == true) {
                    capabilityIndex.remove(capability)
                }
            }
        }
    }

    private fun extractSemanticId(model: ModelDescriptor): String {
        // Extract semantic ID from metadata if available
        return model.semanticId.ifBlank {
            extractSemanticFromModelId(model.id)
        }
    }

    /** M-1: Model ID generation fix - centralize semantic ID logic
     *  Delegates to ModelDescriptor.getEffectiveSemanticId() for consistency
     *  This ensures semantic ID generation is centralized and consistent
     *  across the entire model-catalog module.
     */
    private fun extractSemanticFromModelId(modelId: String): String {
        return extractSemanticId(modelId)
    }

    /**
     * M-1: Centralized semantic ID extraction - static utility method
     * This should be used as the single source of truth for semantic ID generation.
     * Moved the extraction logic from ModelRegistry to be a companion method
     * that can be used consistently across the codebase.
     */
    companion object {
        /**
         * Extract semantic ID from a model ID string.
         * Centralized logic used by both ModelRegistry and ModelDescriptor.
         *
         * @param modelId The model ID to extract from (e.g., "gpt-4-turbo-2024-04-09")
         * @return The semantic ID (e.g., "gpt-4")
         */
        fun extractSemanticId(modelId: String): String {
            // Normalize model ID to semantic form
            // e.g., "gpt-4-turbo-2024-04-09" -> "gpt-4"
            // e.g., "claude-3-opus-20240229" -> "claude-3"
            return when {
                modelId.startsWith("gpt-4") -> "gpt-4"
                modelId.startsWith("gpt-3.5") -> "gpt-3.5"
                modelId.startsWith("claude-3") -> {
                    when {
                        modelId.contains("opus") -> "claude-3-opus"
                        modelId.contains("sonnet") -> "claude-3-sonnet"
                        modelId.contains("haiku") -> "claude-3-haiku"
                        else -> "claude-3"
                    }
                }
                modelId.startsWith("llama") -> extractLlamaVersion(modelId)
                modelId.contains("mistral") -> "mistral"
                modelId.contains("gemini") -> extractGeminiVersion(modelId)
                else -> modelId.split("-").take(2).joinToString("-")
            }
        }

        private fun extractLlamaVersion(id: String): String {
            // Extract llama version from strings like "llama-3-8b-instruct"
            val parts = id.split("-")
            val versionIndex = parts.indexOfFirst { it.startsWith("llama") }
            return if (versionIndex >= 0 && versionIndex + 1 < parts.size) {
                "${parts[versionIndex]}-${parts[versionIndex + 1]}"
            } else "llama"
        }

        private fun extractGeminiVersion(id: String): String {
            return when {
                id.contains("pro") -> "gemini-pro"
                id.contains("flash") -> "gemini-flash"
                id.contains("ultra") -> "gemini-ultra"
                else -> "gemini"
            }
        }
    }

    private fun ModelDescriptor.hasCapability(capability: Capability): Boolean {
        return capabilities.contains(capability)
    }

    private fun updateModelsFlow() {
        _modelsFlow.value = models.values.toList()
    }

    private fun updateCapabilitiesFlow() {
        _capabilitiesFlow.value = getAllCapabilities()
    }
}
