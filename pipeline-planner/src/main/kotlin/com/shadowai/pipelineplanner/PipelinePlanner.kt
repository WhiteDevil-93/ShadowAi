package com.shadowai.pipelineplanner

import com.shadowai.core.Modality
import com.shadowai.core.ProviderExecutor
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Plans multimodal transform sequences and resolves the best executor for each transform.
 */
@Singleton
class PipelinePlanner @Inject constructor(
    private val graph: PipelineGraph
) : PipelinePlanner.ProviderExecutorSource {

    /**
     * Source used by [PipelineExecutor] to resolve executors at runtime.
     */
    interface ProviderExecutorSource {
        suspend fun getBestExecutor(transform: Transform): ProviderExecutor?
    }

    private val executors = mutableSetOf<ProviderExecutor>()

    /**
     * Registers an executor that can be selected for pipeline stages.
     */
    fun registerExecutor(executor: ProviderExecutor) {
        executors += executor
    }

    /**
     * Removes a previously registered executor.
     */
    fun unregisterExecutor(providerId: ProviderId) {
        executors.removeIf { it.providerId == providerId }
    }

    /**
     * Clears all registered executors.
     */
    fun clearExecutors() {
        executors.clear()
    }

    /**
     * Builds a transform graph from static transformation rules.
     */
    fun createTransformGraph(): PipelineGraph {
        graph.clear()

        TransformationRules.getAllRules().forEach { rule ->
            if (rule.transforms.isNotEmpty()) {
                rule.transforms.forEachIndexed { index, transform ->
                    graph.addTransform(
                        transform = transform,
                        cost = rule.cost + (index * 0.1f),
                        priority = rule.priority
                    )
                }
            }
        }

        return graph
    }

    /**
     * Plans a transform sequence from input modality to output modality.
     */
    fun planPipeline(
        inputModality: Modality,
        outputModality: Modality,
        policy: RoutingPolicy = RoutingPolicy.BALANCED
    ): PipelinePlan? {
        val transforms = createTransformGraph().findShortestPath(inputModality, outputModality)
            ?: TransformationRules.findRule(inputModality, outputModality)?.transforms
            ?: return null

        return when {
            transforms.isEmpty() -> PipelinePlan.empty()
            transforms.size == 1 -> PipelinePlan.single(transforms.first(), executor = null)
            else -> PipelinePlan.multi(transforms, executor = null)
        }
    }

    /**
     * Returns true when at least one rule exists for a direct modality pair.
     */
    fun hasDirectTransform(from: Modality, to: Modality): Boolean {
        return TransformationRules.findRule(from, to) != null
    }

    /**
     * Returns executors that can handle a transform class.
     *
     * This method probes executors against known transform variants.
     */
    fun getAdaptersForTransform(transformType: Class<out Transform>): List<ProviderExecutor> {
        val probe = KNOWN_TRANSFORMS.firstOrNull { it::class.java == transformType } ?: return emptyList()
        return executors.filter { executor ->
            runCatching { runBlocking { executor.canExecute(probe) } }.getOrDefault(false)
        }
    }

    override suspend fun getBestExecutor(transform: Transform): ProviderExecutor? {
        var best: ProviderExecutor? = null
        var bestPriority = Int.MIN_VALUE

        for (executor in executors) {
            val available = runCatching { executor.isAvailable() }.getOrDefault(false)
            if (!available) continue

            val canExecute = runCatching { executor.canExecute(transform) }.getOrDefault(false)
            if (!canExecute) continue

            val priority = runCatching { executor.getPriority(transform) }.getOrDefault(0)
            if (priority > bestPriority) {
                best = executor
                bestPriority = priority
            }
        }

        return best
    }

    companion object {
        private val KNOWN_TRANSFORMS = listOf(
            Transform.TextToImage(),
            Transform.ImageToVideo(),
            Transform.TextToVideo(),
            Transform.ImageToImage(),
            Transform.TextToText(),
            Transform.TextToAudio(),
            Transform.AudioToText(),
            Transform.ImageToText(),
            Transform.VideoToText()
        )
    }
}

/**
 * Routing policy placeholder for future cost/latency aware path ranking.
 */
enum class RoutingPolicy {
    MIN_LATENCY,
    MIN_COST,
    MAX_QUALITY,
    BALANCED
}
