package com.shadowai.core

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
 * @property latencyScore Expected response time (1-10, 10=fastest)
 * @property qualityScore Output quality rating (1-10, 10=highest)
 * @property costScore Cost per 1K tokens (1-10, 10=cheapest)
 * @property contextWindow Maximum context size in tokens
 * @property typicalSpeed Tokens per second throughput
 */
data class PerformanceProfile(
    val latencyScore: Int = 5,
    val qualityScore: Int = 5,
    val costScore: Int = 5,
    val contextWindow: Int = 4096,
    val typicalSpeed: Int = 50 // tokens/sec
) {
    init {
        require(latencyScore in 1..10) { "Latency score must be 1-10" }
        require(qualityScore in 1..10) { "Quality score must be 1-10" }
        require(costScore in 1..10) { "Cost score must be 1-10" }
    }
}

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
