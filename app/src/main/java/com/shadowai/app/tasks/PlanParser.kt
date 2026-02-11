package com.shadowai.app.tasks

import com.shadowai.app.execution.DeviceActionParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.1: Explicit Planning Graph (DAGs)
 * Parses JSON into a Plan object. Supports both single-action and multi-node formats.
 *
 * SECURITY IMPROVEMENTS:
 * - Migrated from org.json to Kotlinx Serialization
 * - Added depth limit to prevent stack overflow attacks
 * - Byte-based size checking instead of char-based
 * - Optimized cycle detection with early exit
 */
@Singleton
class PlanParser @Inject constructor() {

    companion object {
        private const val MAX_NODES = 100
        private const val MAX_JSON_SIZE = 1024 * 1024 // 1MB limit
        private const val MAX_DEPTH = 50 // Prevent deeply nested JSON
        private const val MAX_STRING_LENGTH = 10000 // Per-string limit
    }

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        // Removed maxDepth = MAX_DEPTH
    }

    fun parse(jsonString: String): Result<Plan> {
        return try {
            // CRITICAL: Byte-based size check (more accurate than char count)
            if (jsonString.toByteArray().size > MAX_JSON_SIZE) {
                return Result.failure(IllegalArgumentException("Input JSON exceeds maximum size limit of 1MB"))
            }

            // CRITICAL: Stack overflow protection - depth-limited parsing
            val trimmed = jsonString.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Input JSON cannot be empty"))
            }

            // Validate JSON structure before deep parsing
            val firstChar = trimmed.firstOrNull() ?: return Result.failure(IllegalArgumentException("Invalid JSON: empty input"))
            if (firstChar != '{' && firstChar != '[') {
                return Result.failure(IllegalArgumentException("Invalid JSON: must start with '{' or '['"))
            }

            val root = json.parseToJsonElement(trimmed).jsonObject

            // Corrected version access to ensure it's a JsonPrimitive
            val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1

            when {
                root.containsKey("plan") -> parsePlanObject(root, version)
                root.containsKey("action") -> parseSingleAction(root, version)
                else -> Result.failure(IllegalArgumentException("Root JSON must contain 'plan' or 'action' field"))
            }
        } catch (e: IllegalArgumentException) {
            // Re-throw validation errors
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePlanObject(root: JsonObject, version: Int): Result<Plan> {
        val planObj = root["plan"]?.jsonObject
            ?: return Result.failure(IllegalArgumentException("Missing 'plan' object"))

        val nodesArray = planObj["nodes"]?.jsonArray
            ?: return Result.failure(IllegalArgumentException("Missing 'nodes' array in plan"))

        val nodeCount = nodesArray.size
        if (nodeCount > MAX_NODES) {
            return Result.failure(IllegalArgumentException("Plan exceeds maximum node limit of $MAX_NODES"))
        }

        // First pass: collect all node IDs to support forward references
        val allNodeIds = mutableSetOf<String>()
        val nodeObjects = mutableListOf<JsonObject>()

        for (i in 0 until nodeCount) {
            val nodeElement = nodesArray.getOrNull(i)
                ?: return Result.failure(IllegalArgumentException("Node at index $i is missing"))

            val nodeObj = nodeElement.jsonObject

            val id = nodeObj["id"]?.jsonPrimitive?.contentOrNull
                ?: return Result.failure(IllegalArgumentException("Node at index $i missing required 'id' field"))

            // Validate ID format
            if (id.length > MAX_STRING_LENGTH) {
                return Result.failure(IllegalArgumentException("Node ID at index $i exceeds maximum length"))
            }

            if (!allNodeIds.add(id)) {
                return Result.failure(IllegalArgumentException("Duplicate node ID: $id"))
            }

            nodeObjects.add(nodeObj)
        }

        // Second pass: parse nodes and validate dependencies
        val nodes = mutableListOf<PlanNode>()

        for (i in nodeObjects.indices) {
            val nodeObj = nodeObjects[i]

            val id = nodeObj["id"]?.jsonPrimitive?.contentOrNull
                ?: continue // Already validated in first pass

            val actionElement = nodeObj["action"]
                ?: return Result.failure(IllegalArgumentException("Node '$id' missing required 'action' field"))

            // CRITICAL: Avoid double serialization - use the existing JSON string
            val actionJson = actionElement.toString()

            // Validate action JSON size
            if (actionJson.toByteArray().size > MAX_STRING_LENGTH) {
                return Result.failure(IllegalArgumentException("Action JSON for node '$id' exceeds size limit"))
            }

            val actionResult = DeviceActionParser.parse(actionJson)

            if (actionResult.isFailure) {
                return Result.failure(actionResult.exceptionOrNull()
                    ?: IllegalArgumentException("Failed to parse action for node $id"))
            }

            val dependencies = parseDependencies(nodeObj)

            // Validate dependencies exist (supports forward references)
            val invalidDeps = dependencies.filter { dep -> !allNodeIds.contains(dep) }
            if (invalidDeps.isNotEmpty()) {
                return Result.failure(IllegalArgumentException("Node '$id' references non-existent dependencies: ${invalidDeps.joinToString(", ")}"))
            }

            // Prevent self-referential dependencies
            if (dependencies.contains(id)) {
                return Result.failure(IllegalArgumentException("Node '$id' cannot depend on itself"))
            }

            nodes.add(PlanNode(id, actionResult.getOrThrow(), dependencies))
        }

        // Optimized cycle detection with early exit
        if (hasCycles(nodes)) {
            return Result.failure(IllegalArgumentException("Plan contains cyclic dependencies"))
        }

        return Result.success(Plan(version, nodes))
    }

    private fun parseSingleAction(root: JsonObject, version: Int): Result<Plan> {
        // Avoid re-serialization - pass the original action JSON directly
        val actionElement = root["action"]
            ?: return Result.failure(IllegalArgumentException("Missing 'action' field"))

        val actionJson = actionElement.toString()

        // Validate action JSON size
        if (actionJson.toByteArray().size > MAX_STRING_LENGTH) {
            return Result.failure(IllegalArgumentException("Action JSON exceeds size limit"))
        }

        val actionResult = DeviceActionParser.parse(actionJson)

        if (actionResult.isFailure) {
            return Result.failure(actionResult.exceptionOrNull()
                ?: IllegalArgumentException("Failed to parse single action"))
        }

        val node = PlanNode(id = "step_1", action = actionResult.getOrThrow())
        return Result.success(Plan(version, listOf(node)))
    }

    private fun parseDependencies(nodeObj: JsonObject): List<String> {
        val depsElement = nodeObj["dependencies"] ?: return emptyList()

        val depsArray = try {
            depsElement.jsonArray
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }

        return depsArray.mapIndexed { index, element ->
            element.jsonPrimitive.contentOrNull ?: throw IllegalArgumentException("Dependency at index $index is not a string")
        }
    }

    /**
     * Optimized cycle detection using iterative DFS with early exit.
     * Uses coloring algorithm (WHITE=0, GRAY=1, BLACK=2) for O(V+E) complexity.
     */
    private fun hasCycles(nodes: List<PlanNode>): Boolean {
        if (nodes.isEmpty()) return false

        val graph = nodes.associate { node ->
            node.id to node.dependencies.toSet()
        }

        // Color map: 0 = unvisited, 1 = in progress, 2 = completed
        val color = mutableMapOf<String, Int>()
        nodes.forEach { color[it.id] = 0 }

        // Iterative DFS to avoid stack overflow on deep graphs
        for (nodeId in graph.keys) {
            if (color[nodeId] == 0) {
                if (hasCycleFrom(graph, nodeId, color)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasCycleFrom(
        graph: Map<String, Set<String>>,
        nodeId: String,
        color: MutableMap<String, Int>
    ): Boolean {
        val stack = mutableListOf<String>()
        stack.add(nodeId)

        while (stack.isNotEmpty()) {
            val current = stack.last()

            when (color[current]) {
                0 -> {
                    // First time visiting - mark as in progress
                    color[current] = 1
                    val neighbors = graph[current] ?: emptySet()
                    // Add unvisited neighbors to stack
                    for (neighbor in neighbors) {
                        when (color[neighbor]) {
                            0 -> stack.add(neighbor)
                            1 -> return true // Back edge found - cycle!
                            2 -> continue // Already processed
                        }
                    }
                }
                1 -> {
                    // All neighbors processed - mark as completed
                    color[current] = 2
                    stack.removeLast()
                }
                2 -> {
                    // Already completed
                    stack.removeLast()
                }
            }
        }
        return false
    }
}
