package com.shadowai.app.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the runtime registration and discovery of all available tools for the agent.
 * Fulfills the "Create tool registry with runtime registration" and "Allow models to query available tools" requirements.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val gson: Gson
) {
    private val TAG = "ToolRegistry"
    private val tools: MutableMap<String, ToolFunction> = mutableMapOf()

    /**
     * Registers a new tool function with the registry.
     * If a tool with the same name already exists, it is replaced.
     */
    fun registerTool(toolFunction: ToolFunction) {
        val name = toolFunction.descriptor.name
        if (tools.containsKey(name)) {
            Log.w(TAG, "Tool '$name' already registered. Overwriting with new version ${toolFunction.descriptor.version}.")
        }
        tools[name] = toolFunction
        Log.i(TAG, "Tool '${name}' (v${toolFunction.descriptor.version}) registered.")
    }

    /**
     * Unregisters a tool function by its name.
     * Returns true if the tool was found and removed, false otherwise.
     */
    fun unregisterTool(name: String): Boolean {
        val removed = tools.remove(name) != null
        if (removed) {
            Log.i(TAG, "Tool '${name}' unregistered.")
        }
        return removed
    }

    /**
     * Unregisters all tools from the registry.
     */
    fun unregisterAllTools() {
        tools.clear()
        Log.i(TAG, "All tools unregistered.")
    }

    /**
     * Retrieves a tool function by its name.
     */
    fun getTool(name: String): ToolFunction? {
        return tools[name]
    }

    /**
     * Returns a list of all available, non-deprecated tool descriptors.
     * This list is intended to be passed to the LLM for tool-use prompting.
     */
    fun getAvailableToolDescriptors(): List<ToolDescriptor> {
        return tools.values
            .map { it.descriptor }
            .filter { !it.isDeprecated }
    }

    /**
     * Returns a list of all registered tool functions.
     */
    fun getAllToolFunctions(): List<ToolFunction> {
        return tools.values.toList()
    }

    /**
     * Converts the list of available tool descriptors into a JSON schema string
     * suitable for LLM function calling APIs (e.g., OpenAI, Gemini).
     * Uses Gson for proper JSON serialization to prevent injection vulnerabilities.
     */
    fun getToolSchemaJson(): String {
        val descriptors = getAvailableToolDescriptors()
        if (descriptors.isEmpty()) return "[]"

        val schemaArray = JsonArray()
        
        descriptors.forEach { descriptor ->
            val toolObject = JsonObject().apply {
                addProperty("name", descriptor.name)
                addProperty("description", descriptor.description)
                addProperty("version", descriptor.version)
                
                val parametersObject = JsonObject().apply {
                    addProperty("type", "object")
                    
                    val propertiesObject = JsonObject()
                    descriptor.parameters.forEach { param ->
                        val paramObject = JsonObject().apply {
                            addProperty("type", param.type)
                            addProperty("description", param.description)
                            addProperty("required", param.isRequired)
                            param.enum?.let { enumValues ->
                                val enumArray = JsonArray()
                                enumValues.forEach { enumArray.add(it) }
                                add("enum", enumArray)
                            }
                        }
                        propertiesObject.add(param.name, paramObject)
                    }
                    add("properties", propertiesObject)
                    
                    val requiredArray = JsonArray()
                    descriptor.parameters.filter { it.isRequired }.forEach {
                        requiredArray.add(it.name)
                    }
                    add("required", requiredArray)
                }
                add("parameters", parametersObject)
            }
            schemaArray.add(toolObject)
        }
        
        return gson.toJson(schemaArray)
    }
}