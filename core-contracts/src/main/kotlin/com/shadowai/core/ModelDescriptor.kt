package com.shadowai.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Metadata descriptor for an AI model.
 * This data class provides all information needed to identify and configure a model.
 *
 * Implements Parcelable for efficient IPC across module boundaries.
 */
@Parcelize
data class ModelDescriptor(
    /**
     * Unique identifier for the model (e.g., "gpt-4", "llama-2-7b-chat").
     */
    val id: String,

    /**
     * Human-readable display name for the model.
     */
    val name: String,

    /**
     * The provider that hosts or serves this model.
     */
    val providerId: ProviderId,

    /**
     * Set of capabilities this model supports.
     */
    val capabilities: Set<Capability>,

    /**
     * Set of transforms this model can perform.
     */
    val supportedTransforms: @RawValue Set<Transform> = emptySet(),

    /**
     * Additional model-specific metadata (e.g., quantization info).
     */
    val metadata: @RawValue Map<String, Any> = emptyMap(),

    /**
     * Maximum context length supported by this model.
     */
    val maxContext: Int = 4096
) : Parcelable {
    /**
     * Returns the number of tokens available for generation given the input token count.
     */
    fun getAvailableGenerationTokens(inputTokens: Int): Int = (maxContext - inputTokens).coerceAtLeast(0)
    /**
     * Returns whether this model supports a specific transform.
     */
    fun supportsTransform(transform: Transform): Boolean = transform in supportedTransforms

    /**
     * Returns whether this model has a specific capability.
     */
    fun hasCapability(capability: Capability): Boolean = capability in capabilities

    /**
     * Returns the display string of the provider.
     */
    fun getProviderDisplayName(): String = providerId.getDisplayName()
}
