package com.shadowai.pipelineplanner

import com.shadowai.core.Modality
import com.shadowai.core.Transform
import com.shadowai.core.Artifact

/**
 * Builder for constructing complex transformation pipelines.
 * Provides a fluent API for defining transformation sequences.
 */
class PipelineBuilder {
    private val stages = mutableListOf<PipelineStage>()
    private var name = "UnnamedPipeline"
    private var description = ""
    private val metadata = mutableMapOf<String, Any>()
    private var parallelEnabled = false

    /**
     * Sets the pipeline name.
     */
    fun named(name: String) = apply {
        this.name = name
    }

    /**
     * Sets the pipeline description.
     */
    fun withDescription(description: String) = apply {
        this.description = description
    }

    /**
     * Adds metadata to the pipeline.
     */
    fun addMetadata(key: String, value: Any) = apply {
        metadata[key] = value
    }

    /**
     * Enables parallel execution of independent stages.
     */
    fun parallel() = apply {
        parallelEnabled = true
    }

    /**
     * Adds a single transformation stage.
     */
    fun transform(transform: Transform) = apply {
        stages.add(
            PipelineStage.SingleTransform(
                transform = transform,
                order = stages.size
            )
        )
    }

    /**
     * Adds a conditional transformation.
     */
    fun transformIf(
        condition: suspend (Artifact) -> Boolean,
        transform: Transform
    ) = apply {
        stages.add(
            PipelineStage.ConditionalTransform(
                condition = condition,
                transform = transform,
                order = stages.size
            )
        )
    }

    /**
     * Adds a choice between multiple transformations.
     */
    fun choose(
        selector: suspend (Artifact) -> Transform?
    ) = apply {
        stages.add(
            PipelineStage.ChooseTransform(
                selector = selector,
                order = stages.size
            )
        )
    }

    /**
     * Adds a fork to process input into multiple branches.
     */
    fun fork(
        transforms: List<Transform>
    ) = apply {
        stages.add(
            PipelineStage.ForkTransform(
                transforms = transforms,
                order = stages.size
            )
        )
    }

    /**
     * Adds a validation stage.
     */
    fun validate(
        validator: suspend (Artifact) -> Boolean,
        errorMessage: String = "Validation failed"
    ) = apply {
        stages.add(
            PipelineStage.ValidationStage(
                validator = validator,
                errorMessage = errorMessage,
                order = stages.size
            )
        )
    }

    /**
     * Adds a caching stage for intermediate results.
     */
    fun cache(key: String) = apply {
        stages.add(
            PipelineStage.CacheStage(
                key = key,
                order = stages.size
            )
        )
    }

    /**
     * Adds a logging/monitoring stage.
     */
    fun monitor(
        logger: suspend (Artifact) -> Unit
    ) = apply {
        stages.add(
            PipelineStage.MonitoringStage(
                logger = logger,
                order = stages.size
            )
        )
    }

    /**
     * Builds the pipeline configuration.
     */
    fun build(): PipelineConfig {
        if (stages.isEmpty()) {
            throw IllegalStateException("Pipeline must have at least one stage")
        }

        return PipelineConfig(
            name = name,
            description = description,
            stages = stages.toList(),
            metadata = metadata.toMap(),
            parallelEnabled = parallelEnabled,
            stageCount = stages.size
        )
    }

    /**
     * Quick transform from source to target modality.
     */
    fun quickTransform(
        source: Modality,
        target: Modality
    ) = apply {
        val rule = TransformationRules.findRule(source, target)
        if (rule != null) {
            for (transform in rule.transforms) {
                stages.add(
                    PipelineStage.SingleTransform(
                        transform = transform,
                        order = stages.size
                    )
                )
            }
        } else {
            throw IllegalArgumentException("No transformation rule found from $source to $target")
        }
    }
}

/**
 * Configuration for a complete pipeline.
 */
data class PipelineConfig(
    val name: String,
    val description: String,
    val stages: List<PipelineStage>,
    val metadata: Map<String, Any>,
    val parallelEnabled: Boolean,
    val stageCount: Int
) {
    val isValid: Boolean
        get() = stages.isNotEmpty()

    /**
     * Gets the estimated complexity of the pipeline.
     */
    fun getComplexity(): PipelineComplexity {
        val hasConditionals = stages.any { it is PipelineStage.ConditionalTransform }
        val hasBranching = stages.any { it is PipelineStage.ForkTransform }
        val hasValidation = stages.any { it is PipelineStage.ValidationStage }

        return PipelineComplexity(
            stageCount = stages.size,
            hasConditionals = hasConditionals,
            hasBranching = hasBranching,
            hasValidation = hasValidation,
            parallelEnabled = parallelEnabled,
            estimatedComplexity = when {
                hasBranching && hasConditionals -> "HIGH"
                hasConditionals || hasBranching -> "MEDIUM"
                else -> "LOW"
            }
        )
    }

    /**
     * Gets a human-readable summary of the pipeline.
     */
    fun summarize(): String {
        val stageSummary = stages.mapIndexed { index, stage ->
            "  ${index + 1}. ${stage.getDescription()}"
        }.joinToString("\n")

        return """
            |Pipeline: $name
            |Duration: $description
            |Parallel: $parallelEnabled
            |Stages:
            |$stageSummary
        """.trimMargin()
    }
}

/**
 * Represents a single stage in a pipeline.
 */
sealed class PipelineStage(open val order: Int) {
    data class SingleTransform(
        val transform: Transform,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Apply ${transform::class.simpleName}"
    }

    data class ConditionalTransform(
        val condition: suspend (Artifact) -> Boolean,
        val transform: Transform,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Conditionally apply ${transform::class.simpleName}"
    }

    data class ChooseTransform(
        val selector: suspend (Artifact) -> Transform?,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Choose transformation based on input"
    }

    data class ForkTransform(
        val transforms: List<Transform>,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Fork into ${transforms.size} parallel transformations"
    }

    data class ValidationStage(
        val validator: suspend (Artifact) -> Boolean,
        val errorMessage: String,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Validate output: $errorMessage"
    }

    data class CacheStage(
        val key: String,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Cache result with key: $key"
    }

    data class MonitoringStage(
        val logger: suspend (Artifact) -> Unit,
        override val order: Int
    ) : PipelineStage(order) {
        override fun getDescription(): String = "Monitor and log intermediate result"
    }

    abstract fun getDescription(): String
}

/**
 * Complexity metrics for a pipeline.
 */
data class PipelineComplexity(
    val stageCount: Int,
    val hasConditionals: Boolean,
    val hasBranching: Boolean,
    val hasValidation: Boolean,
    val parallelEnabled: Boolean,
    val estimatedComplexity: String
) {
    override fun toString(): String = """
        |Pipeline Complexity:
        | - Stages: $stageCount
        | - Conditionals: $hasConditionals
        | - Branching: $hasBranching
        | - Validation: $hasValidation
        | - Parallel: $parallelEnabled
        | - Level: $estimatedComplexity
    """.trimMargin()
}
