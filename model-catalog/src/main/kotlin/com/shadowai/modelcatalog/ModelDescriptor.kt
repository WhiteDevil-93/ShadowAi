package com.shadowai.modelcatalog

import android.os.Parcelable
import com.shadowai.core.Capability
import com.shadowai.core.ProviderId
import kotlinx.parcelize.Parcelize

/**
 * Descriptor for AI models with full metadata and capabilities.
 * 
 * Implements Parcelable for efficient IPC between app components,
 * services, and UI layers without JSON re-serialization.
 *
 * @param id Unique model identifier (format: "provider:model_name")
 * @param name Human-readable display name
 * @param providerId Provider that hosts this model
 * @param maxContext Maximum context window size in tokens (e.g., 4096, 8192, 128000)
 * @param capabilities Set of capabilities this model supports (TEXT, IMAGE_GEN, etc.)
 * @param metadata Additional provider-specific metadata
 * @param performanceProfile Performance characteristics for routing decisions
 * @param semanticId Semantic identifier for deduplication across providers
 */
@Parcelize
data class ModelDescriptor(
    val id: String,
    val name: String,
    val providerId: ProviderId,
    val maxContext: Int = DEFAULT_CONTEXT_SIZE,
    val capabilities: Set<Capability> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val performanceProfile: PerformanceProfile = PerformanceProfile(),
    val semanticId: String = ""
) : Parcelable {
    
    companion object {
        const val DEFAULT_CONTEXT_SIZE = 4096
        const val KEY_SEMANTIC_ID = "semanticId"
        const val KEY_DESCRIPTION = "description"
        const val KEY_VERSION = "version"
        const val KEY_QUANTIZATION = "quantization"
        const val KEY_SIZE_BYTES = "size_bytes"
    }
    
    /**
     * Checks if this model supports a specific capability.
     */
    fun hasCapability(capability: Capability): Boolean {
        return capabilities.contains(capability)
    }
    
    /**
     * Checks if this model supports all of the given capabilities.
     */
    fun hasAllCapabilities(required: Set<Capability>): Boolean {
        return capabilities.containsAll(required)
    }
    
    /**
     * Gets the semantic ID for deduplication.
     * Falls back to extracting from id if semanticId field is empty.
     */
    fun getEffectiveSemanticId(): String {
        if (semanticId.isNotBlank()) return semanticId
        
        // Extract semantic ID from model name
        // e.g., "gpt-4-turbo-2024-04-09" -> "gpt-4"
        return when {
            id.contains("gpt-4") -> "gpt-4"
            id.contains("gpt-3.5") -> "gpt-3.5"
            id.contains("claude-3-opus") -> "claude-3-opus"
            id.contains("claude-3-sonnet") -> "claude-3-sonnet"
            id.contains("claude-3-haiku") -> "claude-3-haiku"
            id.contains("claude-3") -> "claude-3"
            id.contains("llama") -> extractLlamaVersion(id)
            id.contains("mistral") -> "mistral"
            id.contains("gemini") -> extractGeminiVersion(id)
            else -> id.split("-").take(2).joinToString("-")
        }
    }
    
    /**
     * Calculates capability match score for routing decisions.
     * Higher score = better match for required capabilities.
     */
    fun calculateCapabilityScore(required: Set<Capability>): Double {
        if (required.isEmpty()) return 1.0
        
        val matches = required.count { hasCapability(it) }
        val matchRatio = matches.toDouble() / required.size
        
        // Bonus for having additional capabilities beyond requirements (versatility)
        val extraCapabilities = capabilities.size - matches
        val versatilityBonus = (extraCapabilities * 0.05).coerceAtMost(0.2)
        
        return (matchRatio + versatilityBonus).coerceAtMost(1.0)
    }
    
    /**
     * Checks if this model is suitable for a given context length.
     */
    fun canHandleContext(tokenCount: Int): Boolean {
        return maxContext >= tokenCount
    }
    
    /**
     * Gets estimated maximum tokens that can be generated.
     */
    fun getAvailableGenerationTokens(inputTokens: Int): Int {
        return (maxContext - inputTokens).coerceAtLeast(0)
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
    
    /**
     * Builder for creating ModelDescriptor with validation.
     */
    class Builder {
        private var id: String = ""
        private var name: String = ""
        private var providerId: ProviderId? = null
        private var maxContext: Int = DEFAULT_CONTEXT_SIZE
        private var capabilities: MutableSet<Capability> = mutableSetOf()
        private var metadata: MutableMap<String, String> = mutableMapOf()
        
        fun id(id: String) = apply { this.id = id }
        fun name(name: String) = apply { this.name = name }
        fun providerId(providerId: ProviderId) = apply { this.providerId = providerId }
        fun maxContext(maxContext: Int) = apply { 
            require(maxContext > 0) { "maxContext must be positive" }
            this.maxContext = maxContext 
        }
        fun addCapability(capability: Capability) = apply { this.capabilities.add(capability) }
        fun addCapabilities(capabilities: Set<Capability>) = apply { this.capabilities.addAll(capabilities) }
        fun metadata(key: String, value: String) = apply { this.metadata[key] = value }
        fun metadata(metadata: Map<String, String>) = apply { this.metadata.putAll(metadata) }
        
        fun build(): ModelDescriptor {
            require(id.isNotBlank()) { "id is required" }
            require(name.isNotBlank()) { "name is required" }
            require(providerId != null) { "providerId is required" }
            
            return ModelDescriptor(
                id = id,
                name = name,
                providerId = providerId!!,
                maxContext = maxContext,
                capabilities = capabilities.toSet(),
                metadata = metadata.toMap(),
                semanticId = metadata[KEY_SEMANTIC_ID] ?: ""
            )
        }
    }
}

/**
 * Performance characteristics for routing decisions.
 */
@Parcelize
data class PerformanceProfile(
    val averageLatencyMs: Long = 1000L,
    val throughputTokensPerSecond: Float = 10.0f,
    val costPer1KTokens: Float = 0.0f,
    val qualityScore: Float = 0.5f, // 0.0 - 1.0
    val supportsStreaming: Boolean = true,
    val supportsBatching: Boolean = false
) : Parcelable {
    
    /**
     * Calculates composite score for routing optimization.
     * Higher score = better overall performance.
     */
    fun calculateCompositeScore(): Double {
        // Normalize all factors to 0-1 range
        val latencyScore = (1.0 / (1.0 + (averageLatencyMs / 1000.0))).coerceIn(0.0, 1.0)
        val throughputScore = (throughputTokensPerSecond / 100.0).coerceIn(0.0, 1.0)
        val costScore = if (costPer1KTokens == 0.0f) 1.0 else (1.0 / (1.0 + costPer1KTokens)).coerceIn(0.0, 1.0)
        
        return (latencyScore * 0.3) + (throughputScore * 0.3) + (qualityScore * 0.25) + (costScore * 0.15)
    }
}

/**
 * Local model information for file-system discovered models.
 */
@Parcelize
data class LocalModelInfo(
    val path: String,
    val name: String,
    val size: Long,
    val format: String = "gguf",
    val quantization: String = "unknown",
    val lastModified: Long = 0L
) : Parcelable {
    
    fun getDisplaySize(): String {
        return when {
            size >= 1_000_000_000 -> String.format("%.1f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format("%.1f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.1f KB", size / 1_000.0)
            else -> "$size bytes"
        }
    }
    
    /**
     * Checks if this is a valid GGUF file based on extension and size.
     */
    fun isValidGguf(): Boolean {
        return path.endsWith(".gguf", ignoreCase = true) && size > 1_000_000 // Min 1MB
    }
}
