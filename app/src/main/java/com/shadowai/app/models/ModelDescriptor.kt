package com.shadowai.app.models

/**
 * Uniquely identifies an AI model available to the system.
 */
@JvmInline
value class ModelId(val id: String)

/**
 * Describes the capabilities and properties of an AI model.
 * This is an inert descriptor, not a loader.
 *
 * @property id The unique identifier of the model.
 * @property isLocal True if the model runs entirely on-device.
 * @property name Human-readable name of the model.
 * @property capabilities A set of capability tags (e.g., "chat", "vision", "control").
 * @property memoryRequirementMb Estimated memory usage in MB (if local).
 * @property maxContext Maximum context window in tokens.
 */
data class ModelDescriptor(
    val id: ModelId,
    val isLocal: Boolean,
    val name: String,
    val capabilities: Set<String>,
    val memoryRequirementMb: Int? = null,
    val maxContext: Int = 4096
) {
    fun getAvailableGenerationTokens(inputTokens: Int): Int = (maxContext - inputTokens).coerceAtLeast(0)
}
