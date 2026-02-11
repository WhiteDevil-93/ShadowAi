package com.shadowai.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File

private data class ModuleGraph(
    val nodes: List<Project>,
    val edges: Map<Project, Set<Project>>
)

class ModuleBoundariesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project != project.rootProject) return

        val validateTask = project.tasks.register("validateModuleBoundaries") {
            group = "verification"
            description = "Validates module boundary rules and dependency graph."
            doLast {
                val graph = buildModuleGraph(project)
                val violations = mutableListOf<String>()
                violations += checkCoreContractsDependency(graph)
                violations += checkForbiddenDependencies(graph)
                violations += checkAllowedDependencies(graph)
                violations += checkCycles(graph)

                val reportsDir = project.layout.buildDirectory
                    .dir("reports/module-boundaries")
                    .get()
                    .asFile
                reportsDir.mkdirs()

                val reportFile = File(reportsDir, "module-dependency-report.txt")
                reportFile.writeText(buildReport(graph, violations))

                if (violations.isNotEmpty()) {
                    val relativePath = reportFile.relativeTo(project.projectDir)
                    throw GradleException(
                        "Module boundary violations found. See ${relativePath.path} for details."
                    )
                }
            }
        }

        project.tasks.register("moduleDependencyReport") {
            group = "verification"
            description = "Generates the module dependency report without failing the build."
            doLast {
                val graph = buildModuleGraph(project)
                val violations = mutableListOf<String>()
                violations += checkCoreContractsDependency(graph)
                violations += checkForbiddenDependencies(graph)
                violations += checkAllowedDependencies(graph)
                violations += checkCycles(graph)
                val reportsDir = project.layout.buildDirectory
                    .dir("reports/module-boundaries")
                    .get()
                    .asFile
                reportsDir.mkdirs()
                val reportFile = File(reportsDir, "module-dependency-report.txt")
                reportFile.writeText(buildReport(graph, violations))
            }
        }

        project.tasks.register("moduleDependencyGraph") {
            group = "verification"
            description = "Generates a Mermaid graph of module dependencies."
            doLast {
                val graph = buildModuleGraph(project)
                val reportsDir = project.layout.buildDirectory
                    .dir("reports/module-boundaries")
                    .get()
                    .asFile
                reportsDir.mkdirs()
                val graphFile = File(reportsDir, "module-dependency-graph.mmd")
                graphFile.writeText(buildMermaidGraph(graph))
            }
        }

        project.tasks.register("checkModuleBoundaries") {
            group = "verification"
            description = "Runs module boundary validation."
            dependsOn(validateTask)
        }

        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn(validateTask)
        }
    }

    private fun buildModuleGraph(root: Project): ModuleGraph {
        val nodes = root.subprojects.sortedBy { it.path }
        val edges = nodes.associateWith { project -> collectProjectDependencies(root, project) }
        return ModuleGraph(nodes, edges)
    }

    private fun collectProjectDependencies(root: Project, project: Project): Set<Project> {
        val dependencies = mutableSetOf<Project>()
        project.configurations
            .filter { configuration ->
                configuration.isCanBeResolved && configuration.name.contains("CompileClasspath")
            }
            .forEach { configuration ->
                configuration.incoming.resolutionResult.allDependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .mapNotNull { resolved -> resolved.selected.id as? ProjectComponentIdentifier }
                    .mapNotNull { identifier -> root.findProject(identifier.projectPath) }
                    .filter { target -> target.path != project.path }
                    .forEach { target -> dependencies.add(target) }
            }
        return dependencies
    }

    private fun checkCoreContractsDependency(graph: ModuleGraph): List<String> {
        val violations = mutableListOf<String>()
        graph.nodes.forEach { project ->
            if (!isAndroidModule(project)) return@forEach
            if (project.path == ":core-contracts") return@forEach
            val dependencies = graph.edges[project].orEmpty().map { it.path }.toSet()
            if (":core-contracts" !in dependencies) {
                violations.add("${project.path} must depend on :core-contracts")
            }
        }
        return violations
    }

    private fun checkForbiddenDependencies(graph: ModuleGraph): List<String> {
        val violations = mutableListOf<String>()
        val uiModules = setOf(":ui-params", ":ui-composition")
        val providerModules = setOf(":provider-adapters")

        graph.nodes.forEach { project ->
            val dependencies = graph.edges[project].orEmpty().map { it.path }.toSet()
            if (project.path in uiModules && dependencies.any { it in providerModules }) {
                violations.add("${project.path} cannot depend on provider implementations")
            }
            if (project.path in providerModules && dependencies.any { it in uiModules }) {
                violations.add("${project.path} cannot depend on UI modules")
            }
        }
        return violations
    }

    private fun checkAllowedDependencies(graph: ModuleGraph): List<String> {
        val allowed = mapOf(
            ":core-contracts" to emptySet(),
            ":model-catalog" to setOf(":core-contracts"),
            ":provider-adapters" to setOf(":core-contracts", ":model-catalog"),
            ":artifact-system" to setOf(":core-contracts"),
            ":pipeline-planner" to setOf(":core-contracts", ":provider-adapters", ":artifact-system"),
            ":ui-params" to setOf(":core-contracts"),
            ":ui-composition" to setOf(
                ":core-contracts",
                ":ui-params",
                ":pipeline-planner",
                ":artifact-system"
            ),
            ":diagnostics" to setOf(":core-contracts", ":pipeline-planner"),
            ":hot-swapping" to setOf(":core-contracts", ":provider-adapters"),
            ":app" to setOf(
                ":core-contracts",
                ":model-catalog",
                ":provider-adapters",
                ":artifact-system",
                ":pipeline-planner",
                ":ui-params",
                ":diagnostics",
                ":hot-swapping"
            ),
            ":backend" to emptySet()
        )

        val violations = mutableListOf<String>()
        graph.nodes.forEach { project ->
            val allowedDeps = allowed[project.path] ?: return@forEach
            val dependencies = graph.edges[project].orEmpty().map { it.path }.toSet()
            val unexpected = dependencies - allowedDeps
            if (unexpected.isNotEmpty()) {
                violations.add(
                    "${project.path} has disallowed dependencies: ${unexpected.sorted().joinToString(", ") }"
                )
            }
        }
        return violations
    }

    private fun checkCycles(graph: ModuleGraph): List<String> {
        val violations = mutableListOf<String>()
        val visited = mutableSetOf<Project>()
        val stack = mutableSetOf<Project>()

        fun visit(node: Project, path: MutableList<Project>) {
            if (stack.contains(node)) {
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    val cycle = (path.drop(cycleStart) + node)
                        .joinToString(" -> ") { it.path }
                    violations.add("Circular dependency detected: $cycle")
                }
                return
            }
            if (!visited.add(node)) return

            stack.add(node)
            path.add(node)
            graph.edges[node].orEmpty().forEach { visit(it, path) }
            path.removeAt(path.lastIndex)
            stack.remove(node)
        }

        graph.nodes.forEach { if (!visited.contains(it)) visit(it, mutableListOf()) }
        return violations
    }

    private fun buildReport(graph: ModuleGraph, violations: List<String>): String {
        val builder = StringBuilder()
        builder.appendLine("Module Dependency Report")
        builder.appendLine("========================")
        builder.appendLine()
        builder.appendLine("Modules:")
        graph.nodes.forEach { project ->
            val dependencies = graph.edges[project].orEmpty().map { it.path }.sorted()
            val dependencyLine = if (dependencies.isEmpty()) "(none)" else dependencies.joinToString(", ")
            builder.appendLine("- ${project.path} -> $dependencyLine")
        }
        builder.appendLine()
        builder.appendLine("Rule Checks:")
        if (violations.isEmpty()) {
            builder.appendLine("- No violations detected.")
        } else {
            violations.forEach { violation -> builder.appendLine("- $violation") }
        }
        return builder.toString()
    }

    private fun buildMermaidGraph(graph: ModuleGraph): String {
        val builder = StringBuilder()
        builder.appendLine("graph TD")
        val edges = mutableSetOf<String>()
        graph.nodes.forEach { project ->
            val fromId = nodeId(project.path)
            val fromLabel = project.path.removePrefix(":")
            graph.edges[project].orEmpty().forEach { dependency ->
                val toId = nodeId(dependency.path)
                val toLabel = dependency.path.removePrefix(":")
                edges.add("    $fromId[$fromLabel] --> $toId[$toLabel]")
            }
        }
        edges.sorted().forEach { builder.appendLine(it) }
        return builder.toString()
    }

    private fun nodeId(path: String): String =
        path.removePrefix(":").replace("-", "_")

    private fun isAndroidModule(project: Project): Boolean =
        project.plugins.hasPlugin("com.android.library") ||
            project.plugins.hasPlugin("com.android.application")
}
