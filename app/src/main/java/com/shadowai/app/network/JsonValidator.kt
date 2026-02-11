package com.shadowai.app.network

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Lightweight JSON schema validator for API responses.
 *
 * Validates JSON structure without requiring external libraries.
 * Useful for catching malformed API responses before they cause crashes.
 *
 * Example:
 * ```
 * val schema = JsonSchema.obj {
 *     required("id", JsonType.STRING)
 *     required("name", JsonType.STRING)
 *     optional("age", JsonType.NUMBER)
 *     required("roles", JsonType.ARRAY)
 * }
 *
 * val result = JsonValidator.validate(jsonString, schema)
 * if (result.isValid) {
 *     // Safe to parse
 * } else {
 *     Log.e(TAG, "Invalid JSON: ${result.errors}")
 * }
 * ```
 */
object JsonValidator {

    private const val TAG = "JsonValidator"

    /**
     * JSON data types
     */
    enum class JsonType {
        STRING,
        NUMBER,
        BOOLEAN,
        OBJECT,
        ARRAY,
        NULL,
        ANY
    }

    /**
     * Field requirement level
     */
    enum class Requirement {
        REQUIRED,
        OPTIONAL
    }

    /**
     * Schema definition for a JSON object
     */
    data class JsonSchema(
        val type: JsonType = JsonType.OBJECT,
        val fields: Map<String, FieldSchema> = emptyMap(),
        val arrayItemSchema: JsonSchema? = null,
        val minLength: Int? = null,
        val maxLength: Int? = null,
        val min: Number? = null,
        val max: Number? = null,
        val enum: Set<Any>? = null
    ) {
        companion object {
            fun obj(block: SchemaBuilder.() -> Unit): JsonSchema {
                return SchemaBuilder().apply(block).build()
            }

            fun array(itemSchema: JsonSchema): JsonSchema {
                return JsonSchema(
                    type = JsonType.ARRAY,
                    arrayItemSchema = itemSchema
                )
            }

            fun string(
                minLength: Int? = null,
                maxLength: Int? = null,
                enum: Set<String>? = null
            ): JsonSchema {
                return JsonSchema(
                    type = JsonType.STRING,
                    minLength = minLength,
                    maxLength = maxLength,
                    enum = enum
                )
            }

            fun number(min: Number? = null, max: Number? = null): JsonSchema {
                return JsonSchema(
                    type = JsonType.NUMBER,
                    min = min,
                    max = max
                )
            }

            fun boolean(): JsonSchema = JsonSchema(type = JsonType.BOOLEAN)
            fun any(): JsonSchema = JsonSchema(type = JsonType.ANY)
        }
    }

    /**
     * Field schema definition
     */
    data class FieldSchema(
        val name: String,
        val type: JsonType,
        val requirement: Requirement,
        val schema: JsonSchema? = null,
        val description: String? = null
    )

    /**
     * Builder for creating schemas
     */
    class SchemaBuilder {
        private val fields = mutableMapOf<String, FieldSchema>()

        fun required(name: String, type: JsonType, schema: JsonSchema? = null, description: String? = null) {
            fields[name] = FieldSchema(name, type, Requirement.REQUIRED, schema, description)
        }

        fun optional(name: String, type: JsonType, schema: JsonSchema? = null, description: String? = null) {
            fields[name] = FieldSchema(name, type, Requirement.OPTIONAL, schema, description)
        }

        fun requiredObject(name: String, block: SchemaBuilder.() -> Unit) {
            val schema = SchemaBuilder().apply(block).build()
            required(name, JsonType.OBJECT, schema)
        }

        fun optionalObject(name: String, block: SchemaBuilder.() -> Unit) {
            val schema = SchemaBuilder().apply(block).build()
            optional(name, JsonType.OBJECT, schema)
        }

        fun requiredArray(name: String, itemSchema: JsonSchema) {
            required(name, JsonType.ARRAY, JsonSchema.array(itemSchema))
        }

        fun optionalArray(name: String, itemSchema: JsonSchema) {
            optional(name, JsonType.ARRAY, JsonSchema.array(itemSchema))
        }

        fun build(): JsonSchema = JsonSchema(fields = fields)
    }

    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    ) {
        companion object {
            fun success() = ValidationResult(true)
            fun failure(errors: List<String>) = ValidationResult(false, errors)
            fun failure(error: String) = ValidationResult(false, listOf(error))
        }
    }

    /**
     * Validate JSON string against schema
     */
    fun validate(jsonString: String, schema: JsonSchema): ValidationResult {
        return try {
            when (schema.type) {
                JsonType.OBJECT -> {
                    val obj = JSONObject(jsonString)
                    validateObject(obj, schema, "root")
                }
                JsonType.ARRAY -> {
                    val arr = JSONArray(jsonString)
                    validateArray(arr, schema, "root")
                }
                else -> ValidationResult.failure("Root must be OBJECT or ARRAY")
            }
        } catch (e: JSONException) {
            ValidationResult.failure("Invalid JSON: ${e.message}")
        }
    }

    /**
     * Validate JSONObject against schema
     */
    fun validateObject(obj: JSONObject, schema: JsonSchema, path: String = ""): ValidationResult {
        val errors = mutableListOf<String>()

        // Check required fields
        schema.fields.forEach { (name, fieldSchema) ->
            val fieldPath = if (path.isEmpty()) name else "$path.$name"

            if (fieldSchema.requirement == Requirement.REQUIRED && !obj.has(name)) {
                errors.add("Missing required field: $fieldPath")
                return@forEach
            }

            if (!obj.has(name)) return@forEach // Optional field not present

            val value = obj.opt(name)

            // Validate type
            val typeValid = when (fieldSchema.type) {
                JsonType.STRING -> value is String
                JsonType.NUMBER -> value is Number
                JsonType.BOOLEAN -> value is Boolean
                JsonType.OBJECT -> value is JSONObject
                JsonType.ARRAY -> value is JSONArray
                JsonType.NULL -> value == null || value == JSONObject.NULL
                JsonType.ANY -> true
            }

            if (!typeValid) {
                errors.add("Invalid type for $fieldPath: expected ${fieldSchema.type}, got ${value?.javaClass?.simpleName}")
                return@forEach
            }

            // Validate nested schema
            fieldSchema.schema?.let { nestedSchema ->
                val nestedResult = when {
                    value is JSONObject && fieldSchema.type == JsonType.OBJECT ->
                        validateObject(value, nestedSchema, fieldPath)
                    value is JSONArray && fieldSchema.type == JsonType.ARRAY ->
                        validateArray(value, nestedSchema, fieldPath)
                    else -> ValidationResult.success()
                }
                errors.addAll(nestedResult.errors)
            }

            // Validate constraints
            validateConstraints(value, schema, fieldPath, errors)
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    /**
     * Validate JSONArray against schema
     */
    private fun validateArray(arr: JSONArray, schema: JsonSchema, path: String): ValidationResult {
        val errors = mutableListOf<String>()

        schema.arrayItemSchema?.let { itemSchema ->
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val itemPath = "$path[$i]"

                when {
                    item is JSONObject && itemSchema.type == JsonType.OBJECT ->
                        errors.addAll(validateObject(item, itemSchema, itemPath).errors)
                    item is JSONArray && itemSchema.type == JsonType.ARRAY ->
                        errors.addAll(validateArray(item, itemSchema, itemPath).errors)
                    else -> {
                        // Validate primitive type
                        val typeValid = when (itemSchema.type) {
                            JsonType.STRING -> item is String
                            JsonType.NUMBER -> item is Number
                            JsonType.BOOLEAN -> item is Boolean
                            JsonType.ANY -> true
                            else -> false
                        }
                        if (!typeValid) {
                            errors.add("Invalid array item type at $itemPath: expected ${itemSchema.type}")
                        }
                    }
                }
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    /**
     * Validate value constraints (min/max, length, enum)
     */
    private fun validateConstraints(
        value: Any?,
        schema: JsonSchema,
        path: String,
        errors: MutableList<String>
    ) {
        when (value) {
            is String -> {
                schema.minLength?.let {
                    if (value.length < it) {
                        errors.add("$path length ${value.length} is less than minimum $it")
                    }
                }
                schema.maxLength?.let {
                    if (value.length > it) {
                        errors.add("$path length ${value.length} exceeds maximum $it")
                    }
                }
                schema.enum?.let {
                    if (value !in it) {
                        errors.add("$path value '$value' not in allowed values: $it")
                    }
                }
            }
            is Number -> {
                schema.min?.let {
                    if (value.toDouble() < it.toDouble()) {
                        errors.add("$path value $value is less than minimum $it")
                    }
                }
                schema.max?.let {
                    if (value.toDouble() > it.toDouble()) {
                        errors.add("$path value $value exceeds maximum $it")
                    }
                }
            }
        }
    }

    /**
     * Quick validation - returns true/false without detailed errors
     */
    fun isValid(jsonString: String, schema: JsonSchema): Boolean {
        return validate(jsonString, schema).isValid
    }

    /**
     * Safe parse - validates before parsing
     */
    inline fun <reified T> safeParse(
        jsonString: String,
        schema: JsonSchema,
        parser: (JSONObject) -> T
    ): Result<T> {
        return try {
            val validationResult = validate(jsonString, schema)
            if (!validationResult.isValid) {
                Result.failure(
                    IllegalArgumentException("JSON validation failed: ${validationResult.errors.joinToString(", ")}")
                )
            } else {
                val obj = JSONObject(jsonString)
                Result.success(parser(obj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Common API response schemas
 */
object CommonSchemas {

    /**
     * Standard error response schema
     */
    val errorResponse = JsonValidator.JsonSchema.obj {
        required("error", JsonValidator.JsonType.STRING)
        optional("code", JsonValidator.JsonType.STRING)
        optional("message", JsonValidator.JsonType.STRING)
        optional("details", JsonValidator.JsonType.OBJECT)
    }

    /**
     * Paginated response schema
     */
    fun paginatedResponse(itemSchema: JsonValidator.JsonSchema) = JsonValidator.JsonSchema.obj {
        requiredArray("items", itemSchema)
        required("total", JsonValidator.JsonType.NUMBER)
        optional("page", JsonValidator.JsonType.NUMBER)
        optional("pageSize", JsonValidator.JsonType.NUMBER)
        optional("hasMore", JsonValidator.JsonType.BOOLEAN)
    }

    /**
     * Chat completion response (OpenAI-like)
     */
    val chatCompletionResponse = JsonValidator.JsonSchema.obj {
        required("id", JsonValidator.JsonType.STRING)
        required("object", JsonValidator.JsonType.STRING)
        required("created", JsonValidator.JsonType.NUMBER)
        required("model", JsonValidator.JsonType.STRING)
        requiredArray("choices", JsonValidator.JsonSchema.obj {
            required("index", JsonValidator.JsonType.NUMBER)
            requiredObject("message") {
                required("role", JsonValidator.JsonType.STRING)
                required("content", JsonValidator.JsonType.STRING)
            }
            required("finish_reason", JsonValidator.JsonType.STRING)
        })
        optionalObject("usage") {
            required("prompt_tokens", JsonValidator.JsonType.NUMBER)
            required("completion_tokens", JsonValidator.JsonType.NUMBER)
            required("total_tokens", JsonValidator.JsonType.NUMBER)
        }
    }
}
