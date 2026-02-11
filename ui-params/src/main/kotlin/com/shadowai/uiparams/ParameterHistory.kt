package com.shadowai.uiparams

import java.io.Serializable
import java.util.Date

/**
 * Tracks history of parameter changes over time.
 * Maintains snapshots of parameter values for undo/redo and audit trails.
 */
class ParameterHistory(
    private val maxSnapshots: Int = 50
) {
    /**
     * A snapshot of parameter values at a point in time.
     */
    data class Snapshot(
        val timestamp: Long = System.currentTimeMillis(),
        val values: Map<String, Any?> = emptyMap(),
        val label: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    ) : Serializable {
        /**
         * Gets date of the snapshot.
         */
        fun getDate(): Date = Date(timestamp)
    }

    private val snapshots = mutableListOf<Snapshot>()
    private var currentIndex = -1

    /**
     * Records a new snapshot.
     */
    fun record(
        values: Map<String, Any?>,
        label: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        // Remove future history if we're not at the end
        if (currentIndex < snapshots.size - 1) {
            snapshots.subList(currentIndex + 1, snapshots.size).clear()
        }

        // Add new snapshot
        snapshots.add(
            Snapshot(
                timestamp = System.currentTimeMillis(),
                values = ParameterDiffer().deepCopy(values),
                label = label,
                metadata = metadata
            )
        )

        // Enforce max snapshots
        if (snapshots.size > maxSnapshots) {
            snapshots.removeAt(0)
        }

        currentIndex = snapshots.size - 1
    }

    /**
     * Gets the current snapshot.
     */
    fun getCurrent(): Snapshot? {
        return if (currentIndex >= 0 && currentIndex < snapshots.size) {
            snapshots[currentIndex]
        } else null
    }

    /**
     * Gets all snapshots.
     */
    fun getAll(): List<Snapshot> = snapshots.toList()

    /**
     * Checks if undo is available.
     */
    fun canUndo(): Boolean = currentIndex > 0

    /**
     * Checks if redo is available.
     */
    fun canRedo(): Boolean = currentIndex < snapshots.size - 1

    /**
     * Performs undo operation.
     */
    fun undo(): Snapshot? {
        if (canUndo()) {
            currentIndex--
            return getCurrent()
        }
        return null
    }

    /**
     * Performs redo operation.
     */
    fun redo(): Snapshot? {
        if (canRedo()) {
            currentIndex++
            return getCurrent()
        }
        return null
    }

    /**
     * Navigates to a specific snapshot by index.
     */
    fun navigateTo(index: Int): Snapshot? {
        return if (index >= 0 && index < snapshots.size) {
            currentIndex = index
            getCurrent()
        } else null
    }

    /**
     * Clears all history.
     */
    fun clear() {
        snapshots.clear()
        currentIndex = -1
    }

    /**
     * Gets history size.
     */
    fun size(): Int = snapshots.size

    /**
     * Gets current index.
     */
    fun getCurrentIndex(): Int = currentIndex

    /**
     * Generates diff between two points in history.
     */
    fun getDiff(
        schema: ParameterSchema,
        fromIndex: Int,
        toIndex: Int
    ): ParameterDiffer.DiffResult? {
        val fromSnapshot = snapshots.getOrNull(fromIndex) ?: return null
        val toSnapshot = snapshots.getOrNull(toIndex) ?: return null

        return ParameterDiffer().diff(schema, fromSnapshot.values, toSnapshot.values)
    }

    /**
     * Gets diff between current and previous.
     */
    fun getDiffWithPrevious(schema: ParameterSchema): ParameterDiffer.DiffResult? {
        if (currentIndex < 1) return null
        return getDiff(schema, currentIndex - 1, currentIndex)
    }

    /**
     * Finds snapshots by label.
     */
    fun findByLabel(label: String): List<Snapshot> {
        return snapshots.filter { it.label == label }
    }

    /**
     * Gets snapshots within a time range.
     */
    fun getInTimeRange(startTime: Long, endTime: Long): List<Snapshot> {
        return snapshots.filter { it.timestamp in startTime..endTime }
    }

    /**
     * Creates a labeled snapshot (like a bookmark).
     */
    fun createCheckpoint(label: String, metadata: Map<String, Any> = emptyMap()) {
        val current = getCurrent()
        if (current != null) {
            record(current.values, label, metadata)
        }
    }

    /**
     * Gets state as of a specific timestamp.
     */
    fun getStateAt(timestamp: Long): Snapshot? {
        return snapshots.lastOrNull { it.timestamp <= timestamp }
    }

    /**
     * Exports history as JSON-serializable data.
     */
    fun export(): Map<String, Any> {
        return mapOf(
            "snapshots" to snapshots.map {
                mapOf(
                    "timestamp" to it.timestamp,
                    "label" to (it.label ?: ""),
                    "metadata" to it.metadata
                )
            },
            "currentIndex" to currentIndex,
            "size" to snapshots.size
        )
    }

    /**
     * Gets statistics about the history.
     */
    data class Statistics(
        val totalSnapshots: Int = 0,
        val timeSpanMillis: Long = 0,
        val averageChangeSize: Float = 0f,
        val maxChangeSize: Int = 0,
        val labeledCheckpoints: Int = 0
    )

    fun getStatistics(): Statistics {
        if (snapshots.isEmpty()) {
            return Statistics()
        }

        val timeSpan = snapshots.last().timestamp - snapshots.first().timestamp
        val changeCalculator = ParameterDiffer()
        val dummySchema = ParameterSchema(id = "dummy", name = "dummy")

        var totalChangeSize = 0
        var maxChangeSize = 0
        val labeledCount = snapshots.count { it.label != null }

        for (i in 1 until snapshots.size) {
            val diff = changeCalculator.diff(
                dummySchema,
                snapshots[i - 1].values,
                snapshots[i].values
            )
            totalChangeSize += diff.changedCount + diff.addedCount + diff.removedCount
            maxChangeSize = maxOf(maxChangeSize, diff.changedCount + diff.addedCount + diff.removedCount)
        }

        return Statistics(
            totalSnapshots = snapshots.size,
            timeSpanMillis = timeSpan,
            averageChangeSize = if (snapshots.size > 1) totalChangeSize.toFloat() / (snapshots.size - 1) else 0f,
            maxChangeSize = maxChangeSize,
            labeledCheckpoints = labeledCount
        )
    }

    /**
     * Gets timeline of changes.
     */
    fun getTimeline(): List<String> {
        return snapshots.mapIndexed { index, snapshot ->
            val time = Date(snapshot.timestamp)
            val label = snapshot.label ?: "Snapshot $index"
            val isCurrent = index == currentIndex
            val prefix = if (isCurrent) "▶ " else "  "
            "$prefix[$index] $label @ $time"
        }
    }
}
