package com.shadowai.uiparams

import java.io.Serializable

/**
 * Sealed class representing a parameter that can be rendered as a dynamic UI control.
 * Supports multiple parameter types with validation and metadata.
 */
sealed class ModelParameter : Serializable {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val defaultValue: Any?
    abstract val isRequired: Boolean
    abstract val metadata: Map<String, Any>

    /**
     * String parameter for text input.
     */
    data class StringParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: String = "",
        override val isRequired: Boolean = false,
        val minLength: Int = 0,
        val maxLength: Int = Int.MAX_VALUE,
        val pattern: String? = null,
        val hint: String? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: String (default: '$defaultValue')"
    }

    /**
     * Number parameter for numeric input (Int, Float, Double).
     */
    data class NumberParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: Number = 0,
        override val isRequired: Boolean = false,
        val min: Number? = null,
        val max: Number? = null,
        val step: Number = 1,
        val precision: Int? = null, // decimal places for floats
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: Number (min: $min, max: $max, default: $defaultValue)"
    }

    /**
     * Boolean parameter for toggle/switch control.
     */
    data class BooleanParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: Boolean = false,
        override val isRequired: Boolean = false,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: Boolean (default: $defaultValue)"
    }

    /**
     * Enum parameter for choice selection.
     */
    data class EnumParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: String = "",
        override val isRequired: Boolean = false,
        val options: List<String> = emptyList(),
        val displayNames: Map<String, String> = emptyMap(), // Human-readable names for options
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: Enum (options: $options, default: '$defaultValue')"

        /**
         * Gets display name for an option.
         */
        fun getDisplayName(option: String): String = displayNames[option] ?: option
    }

    /**
     * Array parameter for multiple selections.
     */
    data class ArrayParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: List<Any> = emptyList(),
        override val isRequired: Boolean = false,
        val elementType: String = "string", // string, number, object
        val minItems: Int = 0,
        val maxItems: Int = Int.MAX_VALUE,
        val uniqueItems: Boolean = false,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: Array[$elementType] (min: $minItems, max: $maxItems)"
    }

    /**
     * File parameter for file input/upload.
     */
    data class FileParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: String = "", // File path or URI
        override val isRequired: Boolean = false,
        val acceptedMimeTypes: List<String> = emptyList(), // e.g. ["image/*"]
        val maxSizeBytes: Long = Long.MAX_VALUE,
        val multipleFiles: Boolean = false,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: File (types: $acceptedMimeTypes, max: $maxSizeBytes bytes)"
    }

    /**
     * Object parameter for complex structured data.
     */
    data class ObjectParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: Map<String, Any> = emptyMap(),
        override val isRequired: Boolean = false,
        val schema: Map<String, String> = emptyMap(), // Field name -> type mapping
        val properties: List<ModelParameter> = emptyList(), // Nested parameters
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: Object (fields: ${schema.size})"
    }

    /**
     * Range parameter for slider selection (min-max).
     */
    data class RangeParameter(
        override val id: String,
        override val name: String,
        override val description: String = "",
        override val defaultValue: Pair<Number, Number> = 0 to 100,
        override val isRequired: Boolean = false,
        val min: Number = 0,
        val max: Number = 100,
        val step: Number = 1,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ModelParameter() {
        override fun toString(): String = "$name: Range [$min - $max] (default: $defaultValue)"
    }

    /**
     * Validates a value against this parameter's constraints.
     */
    fun validate(value: Any?): ParameterValidationError? {
        // Check required
        if (isRequired && (value == null || (value is String && value.isEmpty()))) {
            return ParameterValidationError("$name is required")
        }

        if (value == null) return null

        return when (this) {
            is StringParameter -> {
                when {
                    value !is String -> ParameterValidationError("$name must be a string")
                    value.length < minLength -> ParameterValidationError("$name must be at least $minLength characters")
                    value.length > maxLength -> ParameterValidationError("$name must be at most $maxLength characters")
                    pattern != null && !Regex(pattern).matches(value) ->
                        ParameterValidationError("$name does not match required pattern")
                    else -> null
                }
            }
            is NumberParameter -> {
                when {
                    value !is Number -> ParameterValidationError("$name must be a number")
                    min != null && value.toDouble() < min.toDouble() ->
                        ParameterValidationError("$name must be at least $min")
                    max != null && value.toDouble() > max.toDouble() ->
                        ParameterValidationError("$name must be at most $max")
                    else -> null
                }
            }
            is BooleanParameter -> {
                if (value !is Boolean) ParameterValidationError("$name must be a boolean") else null
            }
            is EnumParameter -> {
                if (value !is String || value !in options) {
                    ParameterValidationError("$name must be one of: $options")
                } else null
            }
            is ArrayParameter -> {
                when {
                    value !is List<*> -> ParameterValidationError("$name must be an array")
                    value.size < minItems -> ParameterValidationError("$name must have at least $minItems items")
                    value.size > maxItems -> ParameterValidationError("$name must have at most $maxItems items")
                    uniqueItems && value.size != value.distinct().size ->
                        ParameterValidationError("$name items must be unique")
                    else -> null
                }
            }
            is FileParameter -> {
                if (value !is String) ParameterValidationError("$name must be a file path") else null
            }
            is ObjectParameter -> {
                if (value !is Map<*, *>) ParameterValidationError("$name must be an object") else null
            }
            is RangeParameter -> {
                if (value !is Pair<*, *> || value.first !is Number || value.second !is Number) {
                    ParameterValidationError("$name must be a range pair")
                } else null
            }
        }
    }

    /**
     * Gets a user-friendly display value for this parameter.
     */
    fun getDisplayValue(value: Any?): String {
        return when {
            value == null -> "Not set"
            this is EnumParameter -> getDisplayName(value.toString())
            this is BooleanParameter -> if (value as Boolean) "On" else "Off"
            this is ArrayParameter -> (value as? List<*>)?.size?.toString() + " items" ?: value.toString()
            else -> value.toString()
        }
    }
}

/**
 * Validation error for parameter values.
 */
data class ParameterValidationError(val message: String)

/**
 * Registry of known parameters for transforms.
 */
object ParameterRegistry {
    private val transformParameters = mutableMapOf<String, List<ModelParameter>>()

    /**
     * Registers parameters for a specific model/transform identifier.
     */
    fun register(transformId: String, parameters: List<ModelParameter>) {
        transformParameters[transformId] = parameters
    }

    /**
     * Gets parameters for a model/transform identifier.
     */
    fun getParameters(transformId: String): List<ModelParameter> {
        return transformParameters[transformId] ?: emptyList()
    }

    /**
     * Gets all registered transforms.
     */
    fun listAllTransforms(): List<String> = transformParameters.keys.toList()

    /**
     * Gets all parameters as a schema.
     */
    fun getSchema(transformId: String, name: String = transformId): ParameterSchema {
        return ParameterSchema(
            id = transformId,
            name = name,
            parameters = getParameters(transformId)
        )
    }

    /**
     * Clears all registered parameters.
     */
    fun clear() {
        transformParameters.clear()
    }
}

/**
 * Represents a group of related parameters.
 */
data class ParameterGroup(
    val id: String,
    val name: String,
    val description: String = "",
    val parameters: List<ModelParameter> = emptyList(),
    val collapsible: Boolean = true,
    val collapsed: Boolean = false
)

/**
 * Schema for a collection of parameters.
 */
data class ParameterSchema(
    val id: String,
    val name: String,
    val description: String = "",
    val parameters: List<ModelParameter> = emptyList(),
    val groups: List<ParameterGroup> = emptyList(),
    val presets: Map<String, Map<String, Any>> = emptyMap() // Preset name -> parameter values
) : Serializable {
    /**
     * Gets all parameters (from both root and groups).
     */
    fun getAllParameters(): List<ModelParameter> {
        return parameters + groups.flatMap { it.parameters }
    }

    /**
     * Finds a parameter by ID.
     */
    fun findParameter(parameterId: String): ModelParameter? {
        return getAllParameters().find { it.id == parameterId }
    }

    /**
     * Validates all parameters against provided values.
     */
    fun validateAll(values: Map<String, Any?>): List<ParameterValidationError> {
        return getAllParameters().mapNotNull { param ->
            param.validate(values[param.id])
        }
    }

    /**
     * Gets a preset's values.
     */
    fun getPreset(presetName: String): Map<String, Any>? = presets[presetName]
}
