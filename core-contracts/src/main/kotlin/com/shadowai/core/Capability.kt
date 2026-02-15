package com.shadowai.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Enumeration of AI model capabilities for capability-based routing and selection.
 *
 * Capabilities describe what a model can do and its performance characteristics,
 * enabling intelligent model selection based on task requirements rather than
 * just model names.
 *
 * @see ModelDescriptor.capabilities Set of capabilities a model supports
 * @see ModelRegistry.findModelsByCapability Query by capability
 */
enum class Capability {
    // === Text Generation Capabilities ===

    /** Basic text generation capability (backward compatible alias) */
    TEXT,

    /** Fast text generation, optimized for latency */
    TEXT_GEN_FAST,

    /** High quality text generation, optimized for coherence */
    TEXT_GEN_HIGH_QUALITY,

    /** Long context window support (>8K tokens) */
    CONTEXT_LONG,

    /** Extended context support (>32K tokens) */
    CONTEXT_EXTENDED,

    /** Reasoning and logical inference tasks */
    REASONING,

    /** Code generation and understanding */
    CODE_GEN,

    /** Mathematical computation */
    MATH,

    /** Instruction following precision */
    INSTRUCTION_FOLLOWING,

    /** Complex multi-step task handling */
    COMPLEX_TASKS,

    // === Image Capabilities ===

    /** Image understanding/analysis */
    VISION,

    /** Basic image generation (backward compatible alias) */
    IMAGE_GEN,

    /** Fast image generation */
    IMAGE_GEN_FAST,

    /** High resolution image generation */
    IMAGE_GEN_HIGH_RES,

    /** Image editing/modification */
    IMAGE_EDIT,

    // === Audio Capabilities ===

    /** Speech-to-text transcription */
    AUDIO_TRANSCRIBE,

    /** Text-to-speech synthesis */
    AUDIO_SYNTHESIZE,

    /** Audio understanding/analysis */
    AUDIO_UNDERSTAND,

    // === Video Capabilities ===

    /** Video generation */
    VIDEO_GEN,

    /** Video understanding/analysis */
    VIDEO_UNDERSTAND,

    // === Specialized Capabilities ===

    /** Multi-language support */
    MULTILINGUAL,

    /** JSON/structured output */
    STRUCTURED_OUTPUT,

    /** Function calling / tool use */
    FUNCTION_CALLING,

    /** Streaming response support */
    STREAMING,

    /** Low-cost inference */
    COST_EFFICIENT,

    /** On-device / local inference */
    LOCAL_INFERENCE
}

/**
 * Performance profile for a model.
 *
 * Quantifies model characteristics for selection algorithms.
 *
 * @property latencyScore Expected response time (0.0-1.0, 1.0=fastest)
 * @property qualityScore Output quality rating (0.0-1.0, 1.0=highest)
 * @property costScore Cost per 1K tokens (0.0-1.0, 1.0=cheapest)
 * @property contextWindow Maximum context size in tokens
 * @property averageLatencyMs Average latency in milliseconds
 * @property throughputTokensPerSecond Tokens per second throughput
 */
@Parcelize
data class PerformanceProfile(
    val latencyScore: Float = 0.5f,
    val qualityScore: Float = 0.5f,
    val costScore: Float = 0.5f,
    val contextWindow: Int = 4096,
    val averageLatencyMs: Long = 1000L,
    val throughputTokensPerSecond: Float = 10.0f,
    val reliabilityScore: Float = 0.9f
) : Parcelable

/**
 * Set of capabilities for common task types.
 */
object CapabilitySets {
    /** Capabilities for simple conversation */
    val CONVERSATION = setOf(Capability.TEXT_GEN_FAST)

    /** Capabilities for code generation */
    val CODE_GENERATION = setOf(
        Capability.CODE_GEN,
        Capability.REASONING,
        Capability.CONTEXT_LONG
    )

    /** Capabilities for complex reasoning */
    val REASONING_TASKS = setOf(
        Capability.REASONING,
        Capability.COMPLEX_TASKS,
        Capability.INSTRUCTION_FOLLOWING
    )

    /** Capabilities for creative writing */
    val CREATIVE_WRITING = setOf(
        Capability.TEXT_GEN_HIGH_QUALITY,
        Capability.INSTRUCTION_FOLLOWING
    )

    /** Capabilities for vision tasks */
    val VISION_TASKS = setOf(
        Capability.VISION,
        Capability.TEXT_GEN_FAST
    )

    /** Capabilities for image generation */
    val IMAGE_GENERATION = setOf(
        Capability.IMAGE_GEN
    )

    /** Capabilities for high-quality image generation */
    val HIGH_QUALITY_IMAGE = setOf(
        Capability.IMAGE_GEN_HIGH_RES
    )

    /** Capabilities for local/edge inference */
    val LOCAL = setOf(
        Capability.LOCAL_INFERENCE,
        Capability.COST_EFFICIENT
    )
}
