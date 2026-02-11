package com.shadowai.uiparams

import java.io.Serializable

/**
 * Compares parameter values and detects changes between versions.
 * Useful for detecting modifications and generating change descriptions.
 */
class ParameterDiffer {
    /**
     * Represents a change to a parameter value.
     */
    data class ParameterChange(
        val parameterId: String,
        val parameterName: String,
        val oldValue: Any?,
        val newValue: Any?,
        val changeType: ChangeType = ChangeType.MODIFIED
    ) : Serializable {
        enum class ChangeType {
            ADDED,
            REMOVED,
            MODIFIED,
            UNCHANGED
        }

        /**
         * Gets a human-readable description of the change.
         */
        fun getDescription(): String {
            return when (changeType) {
                ChangeType.ADDED -> "$parameterName: Added ($newValue)"
                ChangeType.REMOVED -> "$parameterName: Removed (was $oldValue)"
                ChangeType.MODIFIED -> "$parameterName: Changed from $oldValue to $newValue"
                ChangeType.UNCHANGED -> "$parameterName: No change"
            }
        }
    }

    /**
     * Diff result containing all changes.
     */
    data class DiffResult(
        val changes: List<ParameterChange> = emptyList(),
        val hasChanges: Boolean = false,
        val changedCount: Int = 0,
        val addedCount: Int = 0,
        val removedCount: Int = 0
    ) : Serializable {
        /**
         * Gets summary of changes.
         */
        fun getSummary(): String {
            if (!hasChanges) return "No changes"
            val parts = mutableListOf<String>()
            if (addedCount > 0) parts.add("+$addedCount")
            if (removedCount > 0) parts.add("-$removedCount")
            if (changedCount > 0) parts.add("~$changedCount modified")
            return parts.joinToString(", ")
        }

        /**
         * Gets changes by type.
         */
        fun getChangesByType(type: ParameterChange.ChangeType): List<ParameterChange> {
            return changes.filter { it.changeType == type }
        }

        /**
         * Gets all descriptions.
         */
        fun getDescriptions(): List<String> {
            return changes.map { it.getDescription() }
        }
    }

    /**
     * Compares two parameter value maps.
     */
    fun diff(
        schema: ParameterSchema,
        oldValues: Map<String, Any?>,
        newValues: Map<String, Any?>
    ): DiffResult {
        val changes = mutableListOf<ParameterChange>()
        val allParamIds = (oldValues.keys + newValues.keys).toSet()

        allParamIds.forEach { parameterId ->
            val parameter = schema.findParameter(parameterId)
            val oldValue = oldValues[parameterId]
            val newValue = newValues[parameterId]

            val change = when {
                oldValue == null && newValue != null -> ParameterChange(
                    parameterId = parameterId,
                    parameterName = parameter?.name ?: parameterId,
                    oldValue = null,
                    newValue = newValue,
                    changeType = ParameterChange.ChangeType.ADDED
                )
                oldValue != null && newValue == null -> ParameterChange(
                    parameterId = parameterId,
                    parameterName = parameter?.name ?: parameterId,
                    oldValue = oldValue,
                    newValue = null,
                    changeType = ParameterChange.ChangeType.REMOVED
                )
                oldValue != newValue -> ParameterChange(
                    parameterId = parameterId,
                    parameterName = parameter?.name ?: parameterId,
                    oldValue = oldValue,
                    newValue = newValue,
                    changeType = ParameterChange.ChangeType.MODIFIED
                )
                else -> ParameterChange(
                    parameterId = parameterId,
                    parameterName = parameter?.name ?: parameterId,
                    oldValue = oldValue,
                    newValue = newValue,
                    changeType = ParameterChange.ChangeType.UNCHANGED
                )
            }

            if (change.changeType != ParameterChange.ChangeType.UNCHANGED) {
                changes.add(change)
            }
        }

        val hasChanges = changes.isNotEmpty()
        val changedCount = changes.count { it.changeType == ParameterChange.ChangeType.MODIFIED }
        val addedCount = changes.count { it.changeType == ParameterChange.ChangeType.ADDED }
        val removedCount = changes.count { it.changeType == ParameterChange.ChangeType.REMOVED }

        return DiffResult(
            changes = changes,
            hasChanges = hasChanges,
            changedCount = changedCount,
            addedCount = addedCount,
            removedCount = removedCount
        )
    }

    /**
     * Patches old values with a diff.
     */
    fun patch(
        oldValues: Map<String, Any?>,
        diffResult: DiffResult
    ): Map<String, Any?> {
        val patchedValues = oldValues.toMutableMap()

        diffResult.changes.forEach { change ->
            when (change.changeType) {
                ParameterChange.ChangeType.ADDED -> patchedValues[change.parameterId] = change.newValue
                ParameterChange.ChangeType.REMOVED -> patchedValues.remove(change.parameterId)
                ParameterChange.ChangeType.MODIFIED -> patchedValues[change.parameterId] = change.newValue
                ParameterChange.ChangeType.UNCHANGED -> {} // No action
            }
        }

        return patchedValues
    }

    /**
     * Compares values and returns a similarity percentage (0-100).
     */
    fun similarity(
        schema: ParameterSchema,
        values1: Map<String, Any?>,
        values2: Map<String, Any?>
    ): Float {
        val allParamIds = (values1.keys + values2.keys).toSet()
        if (allParamIds.isEmpty()) return 100f

        val matchingCount = allParamIds.count { id ->
            values1[id] == values2[id]
        }

        return (matchingCount.toFloat() / allParamIds.size) * 100f
    }

    /**
     * Merges multiple parameter value sets, preferring newer values.
     */
    fun merge(
        vararg valueSets: Map<String, Any?>
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        valueSets.forEach { valueSet ->
            result.putAll(valueSet)
        }

        return result
    }

    /**
     * Filters parameters by a predicate and returns matching changes.
     */
    fun filterChanges(
        diffResult: DiffResult,
        predicate: (ParameterChange) -> Boolean
    ): DiffResult {
        val filtered = diffResult.changes.filter(predicate)
        val changedCount = filtered.count { it.changeType == ParameterChange.ChangeType.MODIFIED }
        val addedCount = filtered.count { it.changeType == ParameterChange.ChangeType.ADDED }
        val removedCount = filtered.count { it.changeType == ParameterChange.ChangeType.REMOVED }

        return DiffResult(
            changes = filtered,
            hasChanges = filtered.isNotEmpty(),
            changedCount = changedCount,
            addedCount = addedCount,
            removedCount = removedCount
        )
    }

    /**
     * Creates a deep copy of parameter values.
     */
    fun deepCopy(values: Map<String, Any?>): Map<String, Any?> {
        return values.mapValues { (_, value) ->
            when (value) {
                null -> null
                is String, is Number, is Boolean -> value
                is List<*> -> value.toList()
                is Map<*, *> -> (value as Map<String, Any?>).let { deepCopy(it) }
                else -> value
            }
        }
    }
}
