package com.shadowai.pipelineplanner

import com.shadowai.core.Modality
import com.shadowai.core.Transform
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a graph of modalities and transformations, enabling multimodal AI pipelines.
 * Implementation for Phase 5: Pipeline Planner & Artifacts.
 */
@Singleton
class PipelineGraph @Inject constructor() {

    private val adjacencyList: MutableMap<Modality, MutableList<TransformEdge>> = mutableMapOf()

    fun addTransform(transform: Transform, cost: Float, priority: Int) {
        val sourceModality = transform.sourceModality
        val targetModality = transform.targetModality
        adjacencyList.getOrPut(sourceModality) { mutableListOf() }
            .add(TransformEdge(targetModality, transform, cost, priority))
    }

    fun findShortestPath(source: Modality, target: Modality): List<Transform>? {
        if (source == target) return emptyList()

        val distances = mutableMapOf<Modality, Double>()
        val previousTransforms = mutableMapOf<Modality, TransformEdge?>()
        val priorityQueue = PriorityQueue<QueueNode>()

        adjacencyList.keys.forEach {
            distances[it] = Double.POSITIVE_INFINITY
            previousTransforms[it] = null
        }

        distances[source] = 0.0
        priorityQueue.add(QueueNode(source, 0.0))

        while (priorityQueue.isNotEmpty()) {
            val (currentModality, currentDistance) = priorityQueue.poll()

            if (currentDistance > (distances[currentModality] ?: Double.POSITIVE_INFINITY)) {
                continue
            }

            adjacencyList[currentModality]?.forEach { edge ->
                val newDistance = currentDistance + edge.cost
                if (newDistance < (distances[edge.targetModality] ?: Double.POSITIVE_INFINITY)) {
                    distances[edge.targetModality] = newDistance
                    previousTransforms[edge.targetModality] = edge
                    priorityQueue.add(QueueNode(edge.targetModality, newDistance))
                }
            }
        }

        return reconstructPath(source, target, previousTransforms)
    }

    private fun reconstructPath(source: Modality, target: Modality, previous: Map<Modality, TransformEdge?>): List<Transform>? {
        val path = LinkedList<Transform>()
        var current: Modality? = target

        while (current != null && current != source) {
            val edge = previous[current] ?: return null
            path.addFirst(edge.transform)
            current = edge.transform.sourceModality
        }

        return if (current == source) path else null
    }

    fun clear() {
        adjacencyList.clear()
    }

    private data class TransformEdge(
        val targetModality: Modality,
        val transform: Transform,
        val cost: Float,
        val priority: Int
    )

    private data class QueueNode(val modality: Modality, val distance: Double) : Comparable<QueueNode> {
        override fun compareTo(other: QueueNode): Int = this.distance.compareTo(other.distance)
    }
}
