package com.shadowai.app.tools

/**
 * Defines the data structure for a single parameter of a tool function.
 */
data class ToolParameter(
    val name: String,
    val type: String, // e.g., "string", "integer", "boolean"
    val description: String,
    val isRequired: Boolean,
    val enum: List<String>? = null
)

/**
 * Defines the metadata and schema for a tool that the LLM can use.
 * Fulfills the "Support tool versioning and deprecation" requirement.
 */
data class ToolDescriptor(
    val name: String,
    val description: String,
    val version: String,
    val parameters: List<ToolParameter>,
    val isDeprecated: Boolean = false,
    val deprecationReason: String? = null
)

/**
 * Represents a concrete implementation of a tool function.
 * This is the executable part that the ToolExecutor will invoke.
 */
interface ToolFunction {
    val descriptor: ToolDescriptor
    suspend fun execute(args: Map<String, Any>): String
}