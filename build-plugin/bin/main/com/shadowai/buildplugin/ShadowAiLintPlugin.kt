package com.shadowai.buildplugin

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for ShadowAi custom lint modules (like ui-validator).
 *
 * Configures the module for lint check development with:
 * - Lint API dependencies (compileOnly)
 * - Auto-service for registration
 * - KSP for annotation processing
 */
class ShadowAiLintPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Apply base library plugin
        pluginManager.apply("shadowai.android.library")
        pluginManager.apply("com.google.devtools.ksp")

        // Configure Android extension
        extensions.configure<LibraryExtension> {
            lint {
                // Prevent lint from checking this lint module
                disable += "all"
            }
        }

        // Add lint-specific dependencies
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            // Lint APIs (compileOnly - don't package with AAR)
            compileOnly(libs.findLibrary("lint-api").get())
            compileOnly(libs.findLibrary("lint-checks").get())

            // For service registration
            implementation(libs.findLibrary("auto-service-annotations").get())
            ksp(libs.findLibrary("autoServiceKspProcessor").get())

            // Testing
            testImplementation(libs.findLibrary("junit").get())
            testImplementation(libs.findLibrary("lint-tests").get())

            // Java annotation API (required by lint API)
            compileOnly("javax.annotation:javax.annotation-api:1.3.2")

            // Gson for JSON parsing in lint checks
            implementation(libs.findLibrary("gson").get())

            // Compose dependencies for analysis
            val composeBom = platform(libs.findLibrary("androidx-compose-bom").get())
            implementation(composeBom)
            implementation(libs.findLibrary("androidx-compose-ui").get())
            implementation(libs.findLibrary("androidx-compose-material3").get())
            implementation(libs.findLibrary("androidx-compose-foundation").get())
            implementation(libs.findLibrary("androidx-compose-material3-window-size").get())
        }
    }
}
