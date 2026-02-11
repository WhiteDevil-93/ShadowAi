package com.shadowai.pipelineplanner

import android.util.Log
import com.shadowai.core.Artifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Executes configured pipelines with support for conditional logic,
 * branching, validation, and monitoring.
 */
class PipelineExecutor {
    private companion object {
        private const val TAG = "PipelineExecutor"
    }

    private val executionCache = ConcurrentHashMap<String, Artifact>()
    private val executionMetrics = mutableListOf<ExecutionMetric>()

    /**
     * Executes a pipeline configuration on an input artifact.
     */
    suspend fun execute(
        config: PipelineConfig,
        input: Artifact,
        executor: PipelinePlanner.ProviderExecutorSource,
        parameters: Map<String, Any> = emptyMap()
    ): Result<Artifact> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Executing pipeline: ${config.name}")
            var current = input
            var elapsedTime = 0L

            for (stage in config.stages) {
                val stageTime = measureTimeMillis {
                    current = executeStage(stage, current, executor, parameters)
                }
                elapsedTime += stageTime

                Log.d(TAG, "Stage ${stage.order} completed in ${stageTime}ms")
            }

            executionMetrics.add(
                ExecutionMetric(
                    pipelineName = config.name,
                    inputModality = input.getModality(),
                    outputModality = current.getModality(),
                    stageCount = config.stages.size,
                    elapsedTime = elapsedTime,
                    success = true
                )
            )

            Result.success(current)
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline execution failed: ${config.name}", e)
            executionMetrics.add(
                ExecutionMetric(
                    pipelineName = config.name,
                    inputModality = input.getModality(),
                    outputModality = null,
                    stageCount = config.stages.size,
                    elapsedTime = 0L,
                    success = false,
                    errorMessage = e.message
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Executes a single pipeline stage.
     */
    private suspend fun executeStage(
        stage: PipelineStage,
        input: Artifact,
        executorSource: PipelinePlanner.ProviderExecutorSource,
        parameters: Map<String, Any>
    ): Artifact {
        return when (stage) {
            is PipelineStage.SingleTransform -> {
                val executor = executorSource.getBestExecutor(stage.transform)
                    ?: throw PipelineExecutionException("No executor found for ${stage.transform::class.simpleName}")

                val result = executor.execute(stage.transform, input, parameters)
                result.getOrThrow()
            }

            is PipelineStage.ConditionalTransform -> {
                if (stage.condition(input)) {
                    Log.d(TAG, "Conditional met. Executing ${stage.transform::class.simpleName}")
                    val executor = executorSource.getBestExecutor(stage.transform)
                        ?: throw PipelineExecutionException("No executor found for ${stage.transform::class.simpleName}")

                    val result = executor.execute(stage.transform, input, parameters)
                    result.getOrThrow()
                } else {
                    Log.d(TAG, "Conditional not met. Skipping stage.")
                    input
                }
            }

            is PipelineStage.ChooseTransform -> {
                val transform = stage.selector(input)
                if (transform != null) {
                    Log.d(TAG, "Selector chose transform: ${transform::class.simpleName}")
                    val executor = executorSource.getBestExecutor(transform)
                        ?: throw PipelineExecutionException("No executor found for ${transform::class.simpleName}")

                    val result = executor.execute(transform, input, parameters)
                    result.getOrThrow()
                } else {
                    Log.d(TAG, "Selector returned null. Skipping stage.")
                    input
                }
            }

            is PipelineStage.ForkTransform -> {
                if (stage.transforms.isEmpty()) return input

                Log.d(TAG, "Forking into ${stage.transforms.size} parallel transforms")

                val results = mutableListOf<Artifact>()
                coroutineScope {
                    for (transform in stage.transforms) {
                        launch {
                            val executor = executorSource.getBestExecutor(transform)
                            if (executor != null) {
                                val result = executor.execute(transform, input, parameters)
                                synchronized(results) {
                                    results.add(result.getOrThrow())
                                }
                            }
                        }
                    }
                }

                // Mixed artifacts were removed from core contracts; encode a merged summary as JSON.
                if (results.size > 1) {
                    mergeAsJsonArtifact(results)
                } else {
                    results.firstOrNull() ?: input
                }
            }

            is PipelineStage.ValidationStage -> {
                if (stage.validator(input)) {
                    Log.d(TAG, "Validation passed")
                    input
                } else {
                    throw ValidationException(stage.errorMessage)
                }
            }

            is PipelineStage.CacheStage -> {
                // Should use ArtifactCache from artifact-system
                // executionCache[stage.key] = input
                Log.d(TAG, "Cached artifact with key: ${stage.key} (STUB)")
                input
            }

            is PipelineStage.MonitoringStage -> {
                stage.logger(input)
                Log.d(TAG, "Monitored stage ${stage.order}")
                input
            }
        }
    }

    /**
     * Gets execution metrics.
     */
    fun getMetrics(): List<ExecutionMetric> = executionMetrics.toList()

    /**
     * Gets metrics for a specific pipeline.
     */
    fun getMetricsFor(pipelineName: String): List<ExecutionMetric> {
        return executionMetrics.filter { it.pipelineName == pipelineName }
    }

    /**
     * Clears execution metrics.
     */
    fun clearMetrics() {
        executionMetrics.clear()
    }

    /**
     * Gets average execution time for a pipeline.
     */
    fun getAverageExecutionTime(pipelineName: String): Long {
        val metrics = getMetricsFor(pipelineName)
        return if (metrics.isNotEmpty()) {
            metrics.map { it.elapsedTime }.average().toLong()
        } else 0L
    }

    /**
     * Gets success rate for a pipeline.
     */
    fun getSuccessRate(pipelineName: String): Double {
        val metrics = getMetricsFor(pipelineName)
        return if (metrics.isNotEmpty()) {
            metrics.count { it.success }.toDouble() / metrics.size
        } else 0.0
    }

    private fun mergeAsJsonArtifact(results: List<Artifact>): Artifact.Json {
        val resultIds = results.joinToString(prefix = "[", postfix = "]") { "\"${it.id}\"" }
        return Artifact.Json(
            id = java.util.UUID.randomUUID().toString(),
            jsonString = resultIds,
            metadata = mapOf(
                "artifact_count" to results.size,
                "artifact_modalities" to results.map { it.getModality().getDisplayName() }
            )
        )
    }
}

/**
 * Metrics for a single pipeline execution.
 */
data class ExecutionMetric(
    val pipelineName: String,
    val inputModality: com.shadowai.core.Modality,
    val outputModality: com.shadowai.core.Modality?,
    val stageCount: Int,
    val elapsedTime: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun toString(): String = """
        |Execution Metric:
        | - Pipeline: $pipelineName
        | - Input: ${inputModality.getDisplayName()}
        | - Output: ${outputModality?.getDisplayName() ?: "N/A"}
        | - Stages: $stageCount
        | - Time: ${elapsedTime}ms
        | - Success: $success
        | - Error: ${errorMessage ?: "None"}
    """.trimMargin()
}

/**
 * Exception thrown when pipeline validation fails.
 */
class ValidationException(message: String) : Exception(message)

/**
 * Exception thrown when pipeline execution fails.
 */
class PipelineExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
