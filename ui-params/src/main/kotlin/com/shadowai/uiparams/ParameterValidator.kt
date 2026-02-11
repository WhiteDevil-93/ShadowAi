package com.shadowai.uiparams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Validates parameter values and schemas.
 * Provides comprehensive validation with severity levels (error, warning, info).
 */
class ParameterValidator {
    /**
     * Validation result with severity level.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationIssue> = emptyList(),
        val warnings: List<ValidationIssue> = emptyList(),
        val info: List<ValidationIssue> = emptyList()
    ) {
        /**
         * Gets all issues sorted by severity.
         */
        fun getAllIssues(): List<ValidationIssue> = errors + warnings + info

        /**
         * Gets formatted error message.
         */
        fun getErrorMessage(): String {
            if (errors.isEmpty()) return "Valid"
            return errors.joinToString("\n") { "• ${it.message}" }
        }
    }

    /**
     * Single validation issue.
     */
    data class ValidationIssue(
        val parameterId: String,
        val message: String,
        val severity: Severity = Severity.ERROR,
        val code: String? = null
    ) {
        enum class Severity { ERROR, WARNING, INFO }
    }

    /**
     * Validates a single parameter value.
     */
    fun validateParameterValue(
        parameter: ModelParameter,
        value: Any?
    ): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        // Check required
        if (parameter.isRequired && (value == null || (value is String && value.isEmpty()))) {
            errors.add(
                ValidationIssue(
                    parameterId = parameter.id,
                    message = "${parameter.name} is required",
                    code = "REQUIRED"
                )
            )
        }

        if (value == null) {
            return ValidationResult(errors.isEmpty(), errors, warnings)
        }

        // Type and constraint validation
        when (parameter) {
            is ModelParameter.StringParameter -> validateString(parameter, value, errors, warnings)
            is ModelParameter.NumberParameter -> validateNumber(parameter, value, errors, warnings)
            is ModelParameter.BooleanParameter -> validateBoolean(parameter, value, errors)
            is ModelParameter.EnumParameter -> validateEnum(parameter, value, errors, warnings)
            is ModelParameter.ArrayParameter -> validateArray(parameter, value, errors, warnings)
            is ModelParameter.FileParameter -> validateFile(parameter, value, errors, warnings)
            is ModelParameter.ObjectParameter -> validateObject(parameter, value, errors, warnings)
            is ModelParameter.RangeParameter -> validateRange(parameter, value, errors, warnings)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Validates all parameters in a schema.
     */
    fun validateAll(
        schema: ParameterSchema,
        values: Map<String, Any?>
    ): ValidationResult {
        val allErrors = mutableListOf<ValidationIssue>()
        val allWarnings = mutableListOf<ValidationIssue>()

        schema.getAllParameters().forEach { param ->
            val result = validateParameterValue(param, values[param.id])
            allErrors.addAll(result.errors)
            allWarnings.addAll(result.warnings)
        }

        return ValidationResult(
            isValid = allErrors.isEmpty(),
            errors = allErrors,
            warnings = allWarnings
        )
    }

    /**
     * Validates all parameters against presets.
     */
    fun validatePresets(
        schema: ParameterSchema
    ): ValidationResult {
        val allErrors = mutableListOf<ValidationIssue>()

        schema.presets.forEach { (presetName, values) ->
            val result = validateAll(schema, values)
            result.errors.forEach { error ->
                allErrors.add(
                    error.copy(
                        message = "Preset '$presetName': ${error.message}"
                    )
                )
            }
        }

        return ValidationResult(isValid = allErrors.isEmpty(), errors = allErrors)
    }

    /**
     * Validates a schema itself.
     */
    fun validateSchema(schema: ParameterSchema): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        // Check for duplicate parameter IDs
        val paramIds = schema.getAllParameters().map { it.id }
        val duplicates = paramIds.groupingBy { it }.eachCount().filter { it.value > 1 }
        duplicates.forEach { (id, count) ->
            warnings.add(
                ValidationIssue(
                    parameterId = id,
                    message = "Parameter '$id' appears $count times (duplicates found)",
                    severity = ValidationIssue.Severity.WARNING,
                    code = "DUPLICATE_ID"
                )
            )
        }

        // Check presets validity
        schema.presets.forEach { (presetName, values) ->
            values.keys.forEach { valueKey ->
                if (schema.findParameter(valueKey) == null) {
                    warnings.add(
                        ValidationIssue(
                            parameterId = valueKey,
                            message = "Preset '$presetName' references unknown parameter '$valueKey'",
                            severity = ValidationIssue.Severity.WARNING,
                            code = "UNKNOWN_PRESET_PARAM"
                        )
                    )
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Validates with async/flow support for long-running validations.
     */
    fun validateAsync(
        schema: ParameterSchema,
        values: Map<String, Any?>
    ): Flow<ValidationResult> = flow {
        val result = validateAll(schema, values)
        emit(result)
    }

    // Private validation methods

    private fun validateString(
        param: ModelParameter.StringParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is String) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be a string, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
            return
        }

        if (value.length < param.minLength) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be at least ${param.minLength} characters (got ${value.length})",
                    code = "MIN_LENGTH"
                )
            )
        }

        if (value.length > param.maxLength) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be at most ${param.maxLength} characters (got ${value.length})",
                    code = "MAX_LENGTH"
                )
            )
        }

        if (param.pattern != null && !Regex(param.pattern).matches(value)) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} does not match required pattern: ${param.pattern}",
                    code = "PATTERN_MISMATCH"
                )
            )
        }
    }

    private fun validateNumber(
        param: ModelParameter.NumberParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is Number) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be a number, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
            return
        }

        val doubleValue = value.toDouble()

        if (param.min != null && doubleValue < param.min.toDouble()) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be at least ${param.min} (got $value)",
                    code = "MIN_VALUE"
                )
            )
        }

        if (param.max != null && doubleValue > param.max.toDouble()) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be at most ${param.max} (got $value)",
                    code = "MAX_VALUE"
                )
            )
        }
    }

    private fun validateBoolean(
        param: ModelParameter.BooleanParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>
    ) {
        if (value !is Boolean) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be a boolean, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
        }
    }

    private fun validateEnum(
        param: ModelParameter.EnumParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is String) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be a string, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
            return
        }

        if (value !in param.options) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be one of: ${param.options.joinToString(", ")} (got '$value')",
                    code = "INVALID_OPTION"
                )
            )
        }
    }

    private fun validateArray(
        param: ModelParameter.ArrayParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is List<*>) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be an array, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
            return
        }

        if (value.size < param.minItems) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must have at least ${param.minItems} items (got ${value.size})",
                    code = "MIN_ITEMS"
                )
            )
        }

        if (value.size > param.maxItems) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must have at most ${param.maxItems} items (got ${value.size})",
                    code = "MAX_ITEMS"
                )
            )
        }

        if (param.uniqueItems && value.size != value.distinct().size) {
            val duplicates = value.groupingBy { it }.eachCount().filter { it.value > 1 }
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} items must be unique (found duplicates: ${duplicates.keys})",
                    code = "DUPLICATE_ITEMS"
                )
            )
        }
    }

    private fun validateFile(
        param: ModelParameter.FileParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is String) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be a file path string, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
        }
    }

    private fun validateObject(
        param: ModelParameter.ObjectParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is Map<*, *>) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be an object, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
        }
    }

    private fun validateRange(
        param: ModelParameter.RangeParameter,
        value: Any?,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ) {
        if (value !is Pair<*, *>) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} must be a range pair, got ${value?.let { it::class.simpleName } ?: "null"}",
                    code = "TYPE_MISMATCH"
                )
            )
            return
        }

        if (value.first !is Number || value.second !is Number) {
            errors.add(
                ValidationIssue(
                    parameterId = param.id,
                    message = "${param.name} pair values must be numbers",
                    code = "TYPE_MISMATCH"
                )
            )
        }
    }
}
