package com.shadowai.uicomposition

import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.pipelineplanner.PipelinePlanner

/**
 * Executes transformations via the pipeline planner.
 */
class TransformationExecutor(
    private val pipelinePlanner: PipelinePlanner
) {
    suspend fun execute(
        input: Artifact,
        targetModality: Modality,
        parameters: Map<String, Any>
    ): Result<Artifact> {
        val plan = pipelinePlanner.planPipeline(input.getModality(), targetModality)
            ?: return Result.failure(
                IllegalStateException(
                    "No pipeline available for ${input.getModality().getDisplayName()} -> ${targetModality.getDisplayName()}"
                )
            )

        if (plan.transforms.isEmpty()) {
            return Result.success(input)
        }

        var currentArtifact = input
        for (transform in plan.transforms) {
            val executor = pipelinePlanner.getBestExecutor(transform)
                ?: return Result.failure(
                    IllegalStateException("No executor available for transform: ${transform::class.simpleName}")
                )

            val result = executor.execute(transform, currentArtifact, parameters)
            if (result.isSuccess) {
                currentArtifact = result.getOrThrow()
            } else {
                val exception = result.exceptionOrNull() ?: RuntimeException("Transformation failed at stage ${transform::class.simpleName}")
                return Result.failure(exception)
            }
        }

        return Result.success(currentArtifact)
    }
}
