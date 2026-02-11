package com.shadowai.uiparams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages parameter state and validation for Compose UI.
 * Provides a single source of truth for parameter values and errors.
 */
class ParameterViewState(
    val schema: ParameterSchema,
    initialValues: Map<String, Any?> = emptyMap()
) {
    private val _values = MutableStateFlow(initialValues)
    val values: StateFlow<Map<String, Any?>> = _values.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, ParameterValidator.ValidationIssue>>(emptyMap())
    val errors: StateFlow<Map<String, ParameterValidator.ValidationIssue>> = _errors.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _lastError = MutableStateFlow<ParameterValidator.ValidationIssue?>(null)
    val lastError: StateFlow<ParameterValidator.ValidationIssue?> = _lastError.asStateFlow()

    private val validator = ParameterValidator()
    private val differ = ParameterDiffer()
    private val history = ParameterHistory()

    init {
        if (initialValues.isNotEmpty()) {
            history.record(initialValues, "Initial state")
        }
    }

    /**
     * Updates a single parameter value.
     */
    fun updateParameter(parameterId: String, value: Any?) {
        val currentValues = _values.value.toMutableMap()
        currentValues[parameterId] = value

        _values.value = currentValues
        _isDirty.value = true

        // Validate the changed parameter
        val parameter = schema.findParameter(parameterId) ?: return
        val result = validator.validateParameterValue(parameter, value)

        val newErrors = _errors.value.toMutableMap()
        if (result.isValid && result.errors.isEmpty()) {
            newErrors.remove(parameterId)
        } else {
            result.errors.firstOrNull()?.let {
                newErrors[parameterId] = it
                _lastError.value = it
            }
        }
        _errors.value = newErrors
    }

    /**
     * Updates multiple parameters at once.
     */
    fun updateParameters(updates: Map<String, Any?>) {
        val currentValues = _values.value.toMutableMap()
        currentValues.putAll(updates)

        _values.value = currentValues
        _isDirty.value = true

        validateAll()
    }

    /**
     * Applies a preset.
     */
    fun applyPreset(presetName: String) {
        val preset = schema.getPreset(presetName) ?: return
        updateParameters(preset)
    }

    /**
     * Resets to initial values.
     */
    fun reset() {
        val snapshot = history.getAll().firstOrNull()
        if (snapshot != null) {
            _values.value = differ.deepCopy(snapshot.values)
            _isDirty.value = false
            _errors.value = emptyMap()
        }
    }

    /**
     * Validates all parameters.
     */
    fun validateAll(): Boolean {
        _isValidating.value = true
        try {
            val result = validator.validateAll(schema, _values.value)
            val errorMap = result.errors.associateBy { it.parameterId }
            _errors.value = errorMap
            return result.isValid
        } finally {
            _isValidating.value = false
        }
    }

    /**
     * Gets validation result for current state.
     */
    fun getValidationResult(): ParameterValidator.ValidationResult {
        return validator.validateAll(schema, _values.value)
    }

    /**
     * Clears errors for a parameter.
     */
    fun clearError(parameterId: String) {
        val newErrors = _errors.value.toMutableMap()
        newErrors.remove(parameterId)
        _errors.value = newErrors
    }

    /**
     * Clears all errors.
     */
    fun clearAllErrors() {
        _errors.value = emptyMap()
        _lastError.value = null
    }

    /**
     * Records current state as a checkpoint.
     */
    fun createCheckpoint(label: String) {
        history.createCheckpoint(label)
    }

    /**
     * Performs undo.
     */
    fun undo() {
        history.undo()?.let {
            _values.value = differ.deepCopy(it.values)
            _isDirty.value = true
        }
    }

    /**
     * Performs redo.
     */
    fun redo() {
        history.redo()?.let {
            _values.value = differ.deepCopy(it.values)
            _isDirty.value = true
        }
    }

    /**
     * Checks if undo is available.
     */
    fun canUndo(): Boolean = history.canUndo()

    /**
     * Checks if redo is available.
     */
    fun canRedo(): Boolean = history.canRedo()

    /**
     * Gets diff between current and initial state.
     */
    fun getDiffFromInitial(): ParameterDiffer.DiffResult {
        val initial = history.getAll().firstOrNull()?.values ?: emptyMap()
        return differ.diff(schema, initial, _values.value)
    }

    /**
     * Gets all historical snapshots.
     */
    fun getHistory(): List<ParameterHistory.Snapshot> = history.getAll()

    /**
     * Gets parameter by ID.
     */
    fun getParameter(parameterId: String): ModelParameter? = schema.findParameter(parameterId)

    /**
     * Gets all parameters.
     */
    fun getAllParameters(): List<ModelParameter> = schema.getAllParameters()

    /**
     * Gets current values.
     */
    fun getCurrentValues(): Map<String, Any?> = _values.value

    /**
     * Gets errors map.
     */
    fun getErrors(): Map<String, ParameterValidator.ValidationIssue> = _errors.value

    /**
     * Exports current state.
     */
    fun export(): String {
        return ParameterSerializer.serializeValues(_values.value)
    }

    /**
     * Imports state from JSON.
     */
    fun import(jsonString: String): Boolean {
        return ParameterSerializer.deserializeValues(jsonString)?.let {
            updateParameters(it)
            true
        } ?: false
    }

    /**
     * Gets validation errors as formatted strings.
     */
    fun getErrorMessages(): List<String> {
        return _errors.value.values.map { it.message }
    }

    /**
     * Checks if all required parameters are set.
     */
    fun hasAllRequiredParameters(): Boolean {
        return schema.getAllParameters()
            .filter { it.isRequired }
            .all { _values.value.containsKey(it.id) && _values.value[it.id] != null }
    }

    /**
     * Gets statistics about current state.
     */
    fun getStatistics(): Map<String, Any> {
        val totalParams = schema.getAllParameters().size
        val filledParams = _values.value.count { it.value != null }
        val errorCount = _errors.value.size
        val requiredCount = schema.getAllParameters().count { it.isRequired }
        val filledRequired = schema.getAllParameters()
            .filter { it.isRequired }
            .count { _values.value[it.id] != null }

        return mapOf(
            "totalParameters" to totalParams,
            "filledParameters" to filledParams,
            "fillPercentage" to if (totalParams > 0) (filledParams * 100 / totalParams) else 0,
            "errorCount" to errorCount,
            "requiredParameters" to requiredCount,
            "filledRequired" to filledRequired,
            "isValid" to (_errors.value.isEmpty() && hasAllRequiredParameters()),
            "isDirty" to _isDirty.value,
            "historySize" to history.size()
        )
    }
}
