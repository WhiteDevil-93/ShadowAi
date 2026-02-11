package com.shadowai.buildplugin

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention plugin for ShadowAi Android library modules with Jetpack Compose.
 *
 * Extends the base library plugin with:
 * - Compose build feature enabled
 * - Compose BOM dependencies
 * - Compose UI, Material3, Foundation
 */
class ShadowAiAndroidLibraryComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Apply base library plugin
        pluginManager.apply("shadowai.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // Configure Android extension
        extensions.configure<LibraryExtension> {
            buildFeatures {
                compose = true
            }
        }

        // Add Compose dependencies
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            val composeBom = platform(libs.findLibrary("androidx-compose-bom").get())
            implementation(composeBom)

            // Compose Core
            implementation(libs.findLibrary("androidx-compose-ui").get())
            implementation(libs.findLibrary("androidx-compose-ui-graphics").get())
            implementation(libs.findLibrary("androidx-compose-material3").get())
            implementation(libs.findLibrary("androidx-compose-foundation").get())
            implementation(libs.findLibrary("androidx-compose-animation").get())
            implementation(libs.findLibrary("androidx-compose-runtime").get())
            implementation(libs.findLibrary("kotlinx-serialization-json").get())

            // Compose Tooling (debug only)
            debugImplementation(libs.findLibrary("androidx-compose-ui-tooling").get())
            debugImplementation(libs.findLibrary("androidx-compose-ui-test-manifest").get())

            // Compose Testing
            androidTestImplementation(libs.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
