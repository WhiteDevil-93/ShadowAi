package com.shadowai.pipelineplanner

import com.shadowai.core.Modality
import com.shadowai.core.Transform

/**
 * Defines all supported transformation rules and paths between modalities.
 * Centralizes transformation logic for graph construction.
 */
object TransformationRules {

    /**
     * Represents a transformation rule.
     */
    data class Rule(
        val source: Modality,
        val target: Modality,
        val description: String,
        val transforms: List<Transform>,
        val cost: Float = 1.0f,
        val priority: Int = 0,
        val requiresInternet: Boolean = false,
        val requiresModel: String? = null
    )

    private val rules = mutableListOf<Rule>()

    init {
        registerDefaultRules()
    }

    /**
     * Registers all default transformation rules.
     */
    private fun registerDefaultRules() {
        // Text-to-Image transformations
        registerRule(
            source = Modality.Text,
            target = Modality.Image,
            description = "Text-to-Image generation using Flux or Replicate",
            transforms = emptyList(), // Will be populated at runtime
            cost = 2.0f,
            priority = 10,
            requiresInternet = true,
            requiresModel = "FLUX_1"
        )

        // Text-to-Audio transformations
        registerRule(
            source = Modality.Text,
            target = Modality.Audio,
            description = "Text-to-Speech conversion",
            transforms = emptyList(),
            cost = 1.5f,
            priority = 8,
            requiresInternet = true
        )

        // Text-to-Video transformations
        registerRule(
            source = Modality.Text,
            target = Modality.Video,
            description = "Text-to-Video generation",
            transforms = emptyList(),
            cost = 3.0f,
            priority = 5,
            requiresInternet = true
        )

        // Image-to-Text transformations (OCR)
        registerRule(
            source = Modality.Image,
            target = Modality.Text,
            description = "Optical Character Recognition (OCR)",
            transforms = emptyList(),
            cost = 1.0f,
            priority = 9,
            requiresModel = "VISION"
        )

        // Audio-to-Text transformations (Speech-to-Text)
        registerRule(
            source = Modality.Audio,
            target = Modality.Text,
            description = "Automatic Speech Recognition (ASR)",
            transforms = emptyList(),
            cost = 1.5f,
            priority = 9,
            requiresInternet = true
        )

        // Video-to-Text transformations
        registerRule(
            source = Modality.Video,
            target = Modality.Text,
            description = "Video understanding and transcription",
            transforms = emptyList(),
            cost = 2.5f,
            priority = 7,
            requiresModel = "VISION"
        )

        // Image-to-Image refinement
        registerRule(
            source = Modality.Image,
            target = Modality.Image,
            description = "Image upscaling, restoration, or style transfer",
            transforms = emptyList(),
            cost = 1.5f,
            priority = 5,
            requiresModel = "IMAGE_UPSCALE"
        )

        // Text-to-Text refinement
        registerRule(
            source = Modality.Text,
            target = Modality.Text,
            description = "Text editing, summarization, translation",
            transforms = emptyList(),
            cost = 0.5f,
            priority = 10,
            requiresInternet = false
        )

        // Video extraction to frame images
        registerRule(
            source = Modality.Video,
            target = Modality.Image,
            description = "Extract keyframes from video",
            transforms = emptyList(),
            cost = 0.5f,
            priority = 8,
            requiresInternet = false
        )

        // Audio feature extraction
        registerRule(
            source = Modality.Audio,
            target = Modality.Audio,
            description = "Audio processing and enhancement",
            transforms = emptyList(),
            cost = 1.0f,
            priority = 5,
            requiresInternet = false
        )

        // Mixed modality handling
        registerRule(
            source = Modality.Mixed,
            target = Modality.Text,
            description = "Multimodal understanding (combining vision + text)",
            transforms = emptyList(),
            cost = 2.0f,
            priority = 9,
            requiresModel = "MULTIMODAL"
        )

        registerRule(
            source = Modality.Text,
            target = Modality.Mixed,
            description = "Generate multimodal content from text",
            transforms = emptyList(),
            cost = 2.5f,
            priority = 7,
            requiresInternet = true
        )
    }

    /**
     * Registers a transformation rule.
     */
    fun registerRule(
        source: Modality,
        target: Modality,
        description: String,
        transforms: List<Transform>,
        cost: Float = 1.0f,
        priority: Int = 0,
        requiresInternet: Boolean = false,
        requiresModel: String? = null
    ) {
        rules.add(
            Rule(
                source = source,
                target = target,
                description = description,
                transforms = transforms,
                cost = cost,
                priority = priority,
                requiresInternet = requiresInternet,
                requiresModel = requiresModel
            )
        )
    }

    /**
     * Finds a rule for transforming from source to target.
     */
    fun findRule(source: Modality, target: Modality): Rule? {
        return rules.filter { it.source == source && it.target == target }
            .minByOrNull { it.cost }
    }

    /**
     * Finds all rules from a source modality.
     */
    fun getRulesFrom(source: Modality): List<Rule> {
        return rules.filter { it.source == source }
            .sortedWith(compareBy({ it.cost }, { -it.priority }))
    }

    /**
     * Finds all rules to a target modality.
     */
    fun getRulesTo(target: Modality): List<Rule> {
        return rules.filter { it.target == target }
            .sortedWith(compareBy({ it.cost }, { -it.priority }))
    }

    /**
     * Gets all available rules.
     */
    fun getAllRules(): List<Rule> = rules.toList()

    /**
     * Gets rules that don't require internet.
     */
    fun getLocalRules(): List<Rule> = rules.filter { !it.requiresInternet }

    /**
     * Gets rules that require a specific model.
     */
    fun getRulesRequiring(modelId: String): List<Rule> {
        return rules.filter { it.requiresModel == modelId }
    }

    /**
     * Clears all registered rules (for testing).
     */
    fun clear() {
        rules.clear()
    }

    /**
     * Gets statistics about registered rules.
     */
    fun getStats(): RuleStats {
        val totalRules = rules.size
        val internetRules = rules.count { it.requiresInternet }
        val localRules = totalRules - internetRules
        val modalities = (rules.map { it.source } + rules.map { it.target }).distinct()
        val avgCost = if (rules.isNotEmpty()) rules.map { it.cost }.average() else 0.0

        return RuleStats(
            totalRules = totalRules,
            internetRules = internetRules,
            localRules = localRules,
            modalities = modalities.size,
            averageCost = avgCost
        )
    }
}

/**
 * Statistics about transformation rules.
 */
data class RuleStats(
    val totalRules: Int,
    val internetRules: Int,
    val localRules: Int,
    val modalities: Int,
    val averageCost: Double
) {
    val internetPercent: Int
        get() = if (totalRules > 0) (internetRules * 100) / totalRules else 0

    override fun toString(): String = """
        |Transformation Rules Statistics:
        | - Total Rules: $totalRules
        | - Internet Rules: $internetRules (${internetPercent}%)
        | - Local Rules: $localRules
        | - Modalities: $modalities
        | - Average Cost: ${String.format("%.2f", averageCost)}
    """.trimMargin()
}
