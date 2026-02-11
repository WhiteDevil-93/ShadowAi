package com.shadowai.uiparams

import kotlinx.serialization.json.*

/**
 * Serializes and deserializes ModelParameter and ParameterSchema to/from JSON.
 * Handles all parameter types and nested structures.
 */
object ParameterSerializer {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Serializes a parameter to JSON string.
     */
    fun serializeParameter(parameter: ModelParameter): String {
        return json.encodeToString(
            JsonElement.serializer(),
            parameterToJson(parameter)
        )
    }

    /**
     * Deserializes a parameter from JSON string.
     */
    fun deserializeParameter(jsonString: String): ModelParameter? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonString)
            jsonToParameter(jsonElement as? JsonObject ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serializes a schema to JSON string.
     */
    fun serializeSchema(schema: ParameterSchema): String {
        return json.encodeToString(JsonElement.serializer(), schemaToJson(schema))
    }

    /**
     * Deserializes a schema from JSON string.
     */
    fun deserializeSchema(jsonString: String): ParameterSchema? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonString)
            jsonToSchema(jsonElement as? JsonObject ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serializes parameter values map to JSON.
     */
    fun serializeValues(values: Map<String, Any?>): String {
        val jsonMap = values.mapValues { (_, v) -> valuesToJson(v) }
        return json.encodeToString(
            JsonElement.serializer(),
            JsonObject(jsonMap)
        )
    }

    /**
     * Deserializes parameter values from JSON.
     */
    fun deserializeValues(jsonString: String): Map<String, Any?>? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonString)
            val jsonObj = jsonElement as? JsonObject ?: return null
            jsonObj.mapValues { (_, v) -> jsonToValue(v) }
        } catch (e: Exception) {
            null
        }
    }

    // Private conversion methods

    private fun parameterToJson(parameter: ModelParameter): JsonElement {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(parameter.id),
                "name" to JsonPrimitive(parameter.name),
                "description" to JsonPrimitive(parameter.description),
                "type" to JsonPrimitive(parameter::class.simpleName),
                "defaultValue" to valuesToJson(parameter.defaultValue),
                "isRequired" to JsonPrimitive(parameter.isRequired),
                "metadata" to metadataToJson(parameter.metadata),
                "properties" to parametersJsonForType(parameter)
            )
        )
    }

    private fun parametersJsonForType(parameter: ModelParameter): JsonElement {
        return when (parameter) {
            is ModelParameter.StringParameter -> JsonObject(
                mapOf(
                    "minLength" to JsonPrimitive(parameter.minLength),
                    "maxLength" to JsonPrimitive(parameter.maxLength),
                    "pattern" to (parameter.pattern?.let { JsonPrimitive(it) } ?: JsonNull),
                    "hint" to (parameter.hint?.let { JsonPrimitive(it) } ?: JsonNull)
                )
            )
            is ModelParameter.NumberParameter -> JsonObject(
                mapOf(
                    "min" to (parameter.min?.let { JsonPrimitive(it.toDouble()) } ?: JsonNull),
                    "max" to (parameter.max?.let { JsonPrimitive(it.toDouble()) } ?: JsonNull),
                    "step" to JsonPrimitive(parameter.step.toDouble()),
                    "precision" to (parameter.precision?.let { JsonPrimitive(it) } ?: JsonNull)
                )
            )
            is ModelParameter.EnumParameter -> JsonObject(
                mapOf(
                    "options" to JsonArray(parameter.options.map { JsonPrimitive(it) }),
                    "displayNames" to JsonObject(
                        parameter.displayNames.mapValues { JsonPrimitive(it.value) }
                    )
                )
            )
            is ModelParameter.ArrayParameter -> JsonObject(
                mapOf(
                    "elementType" to JsonPrimitive(parameter.elementType),
                    "minItems" to JsonPrimitive(parameter.minItems),
                    "maxItems" to JsonPrimitive(parameter.maxItems),
                    "uniqueItems" to JsonPrimitive(parameter.uniqueItems)
                )
            )
            is ModelParameter.FileParameter -> JsonObject(
                mapOf(
                    "acceptedMimeTypes" to JsonArray(
                        parameter.acceptedMimeTypes.map { JsonPrimitive(it) }
                    ),
                    "maxSizeBytes" to JsonPrimitive(parameter.maxSizeBytes),
                    "multipleFiles" to JsonPrimitive(parameter.multipleFiles)
                )
            )
            is ModelParameter.ObjectParameter -> JsonObject(
                mapOf(
                    "schema" to JsonObject(
                        parameter.schema.mapValues { JsonPrimitive(it.value) }
                    ),
                    "properties" to JsonArray(
                        parameter.properties.map { parameterToJson(it) }
                    )
                )
            )
            is ModelParameter.RangeParameter -> JsonObject(
                mapOf(
                    "min" to JsonPrimitive(parameter.min.toDouble()),
                    "max" to JsonPrimitive(parameter.max.toDouble()),
                    "step" to JsonPrimitive(parameter.step.toDouble())
                )
            )
            is ModelParameter.BooleanParameter -> JsonObject(emptyMap())
        }
    }

    private fun jsonToParameter(jsonObj: JsonObject?): ModelParameter? {
        if (jsonObj == null) return null
        val id = jsonObj["id"]?.jsonPrimitive?.content ?: return null
        val name = jsonObj["name"]?.jsonPrimitive?.content ?: return null
        val description = jsonObj["description"]?.jsonPrimitive?.content ?: ""
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: return null
        val defaultValue = jsonObj["defaultValue"]?.let { jsonToValue(it) }
        val isRequired = jsonObj["isRequired"]?.jsonPrimitive?.boolean ?: false
        val metadata = jsonObj["metadata"]?.let { metadataFromJson(it as? JsonObject) } ?: emptyMap()
        val props = jsonObj["properties"] as? JsonObject

        return when (type) {
            "StringParameter" -> ModelParameter.StringParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? String ?: "",
                isRequired = isRequired,
                minLength = props?.get("minLength")?.jsonPrimitive?.int ?: 0,
                maxLength = props?.get("maxLength")?.jsonPrimitive?.int ?: Int.MAX_VALUE,
                pattern = props?.get("pattern")?.jsonPrimitive?.content,
                hint = props?.get("hint")?.jsonPrimitive?.content,
                metadata = metadata
            )
            "NumberParameter" -> ModelParameter.NumberParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? Number ?: 0,
                isRequired = isRequired,
                min = props?.get("min")?.jsonPrimitive?.double,
                max = props?.get("max")?.jsonPrimitive?.double,
                step = props?.get("step")?.jsonPrimitive?.double ?: 1.0,
                precision = props?.get("precision")?.jsonPrimitive?.int,
                metadata = metadata
            )
            "BooleanParameter" -> ModelParameter.BooleanParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? Boolean ?: false,
                isRequired = isRequired,
                metadata = metadata
            )
            "EnumParameter" -> ModelParameter.EnumParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? String ?: "",
                isRequired = isRequired,
                options = (props?.get("options") as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
                displayNames = (props?.get("displayNames") as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                metadata = metadata
            )
            "ArrayParameter" -> ModelParameter.ArrayParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? List<Any> ?: emptyList(),
                isRequired = isRequired,
                elementType = props?.get("elementType")?.jsonPrimitive?.content ?: "string",
                minItems = props?.get("minItems")?.jsonPrimitive?.int ?: 0,
                maxItems = props?.get("maxItems")?.jsonPrimitive?.int ?: Int.MAX_VALUE,
                uniqueItems = props?.get("uniqueItems")?.jsonPrimitive?.boolean ?: false,
                metadata = metadata
            )
            "FileParameter" -> ModelParameter.FileParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? String ?: "",
                isRequired = isRequired,
                acceptedMimeTypes = (props?.get("acceptedMimeTypes") as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
                maxSizeBytes = props?.get("maxSizeBytes")?.jsonPrimitive?.long ?: Long.MAX_VALUE,
                multipleFiles = props?.get("multipleFiles")?.jsonPrimitive?.boolean ?: false,
                metadata = metadata
            )
            "RangeParameter" -> ModelParameter.RangeParameter(
                id = id,
                name = name,
                description = description,
                defaultValue = defaultValue as? Pair<Number, Number> ?: (0 to 100),
                isRequired = isRequired,
                min = props?.get("min")?.jsonPrimitive?.double ?: 0.0,
                max = props?.get("max")?.jsonPrimitive?.double ?: 100.0,
                metadata = metadata
            )
            else -> null
        }
    }

    private fun schemaToJson(schema: ParameterSchema): JsonElement {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(schema.id),
                "name" to JsonPrimitive(schema.name),
                "description" to JsonPrimitive(schema.description),
                "parameters" to JsonArray(schema.parameters.map { parameterToJson(it) }),
                "groups" to JsonArray(
                    schema.groups.map { group ->
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(group.id),
                                "name" to JsonPrimitive(group.name),
                                "description" to JsonPrimitive(group.description),
                                "parameters" to JsonArray(group.parameters.map { parameterToJson(it) }),
                                "collapsible" to JsonPrimitive(group.collapsible),
                                "collapsed" to JsonPrimitive(group.collapsed)
                            )
                        )
                    }
                ),
                "presets" to JsonObject(
                    schema.presets.mapValues { (_, values) ->
                        JsonObject(values.mapValues { valuesToJson(it.value) })
                    }
                )
            )
        )
    }

    private fun jsonToSchema(jsonObj: JsonObject): ParameterSchema? {
        val id = jsonObj["id"]?.jsonPrimitive?.content ?: return null
        val name = jsonObj["name"]?.jsonPrimitive?.content ?: return null
        val description = jsonObj["description"]?.jsonPrimitive?.content ?: ""

        val parameters = (jsonObj["parameters"] as? JsonArray)?.mapNotNull {
            jsonToParameter(it as? JsonObject)
        } ?: emptyList()

        val groups = (jsonObj["groups"] as? JsonArray)?.mapNotNull { groupElem ->
            val groupObj = groupElem as? JsonObject ?: return@mapNotNull null
            val groupId = groupObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val groupName = groupObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val groupDesc = groupObj["description"]?.jsonPrimitive?.content ?: ""
            val groupParams = (groupObj["parameters"] as? JsonArray)?.mapNotNull {
                jsonToParameter(it as? JsonObject)
            } ?: emptyList()
            val collapsible = groupObj["collapsible"]?.jsonPrimitive?.boolean ?: true
            val collapsed = groupObj["collapsed"]?.jsonPrimitive?.boolean ?: false

            ParameterGroup(
                id = groupId,
                name = groupName,
                description = groupDesc,
                parameters = groupParams,
                collapsible = collapsible,
                collapsed = collapsed
            )
        } ?: emptyList()

        val presets = mutableMapOf<String, Map<String, Any>>()
        (jsonObj["presets"] as? JsonObject)?.forEach { (presetName, presetValue) ->
            val presetObj = presetValue as? JsonObject
            if (presetObj != null) {
                val mapped = buildMap<String, Any> {
                    presetObj.forEach { (key, value) ->
                        val parsed = jsonToValue(value)
                        if (parsed != null) {
                            put(key, parsed)
                        }
                    }
                }
                if (mapped.isNotEmpty()) {
                    presets[presetName] = mapped
                }
            }
        }

        return ParameterSchema(
            id = id,
            name = name,
            description = description,
            parameters = parameters,
            groups = groups,
            presets = presets
        )
    }

    private fun valuesToJson(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map { valuesToJson(it) })
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { valuesToJson(it.value) })
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun jsonToValue(element: JsonElement): Any? {
        return when {
            element is JsonNull -> null
            element is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.intOrNull != null -> element.intOrNull
                    element.doubleOrNull != null -> element.doubleOrNull
                    element.booleanOrNull != null -> element.booleanOrNull
                    else -> element.content
                }
            }
            element is JsonArray -> element.map { jsonToValue(it) }
            element is JsonObject -> element.mapValues { jsonToValue(it.value) }
            else -> element.toString()
        }
    }

    private fun metadataToJson(metadata: Map<String, Any>): JsonElement {
        return JsonObject(metadata.mapValues { valuesToJson(it.value) })
    }

    private fun metadataFromJson(jsonObj: JsonObject?): Map<String, Any> {
        if (jsonObj == null) return emptyMap()
        return buildMap {
            jsonObj.forEach { (key, value) ->
                val mapped = jsonToValue(value)
                if (mapped != null) {
                    put(key, mapped)
                }
            }
        }
    }
}
