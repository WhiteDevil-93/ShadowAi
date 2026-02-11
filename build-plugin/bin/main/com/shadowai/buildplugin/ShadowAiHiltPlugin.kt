package com.shadowai.buildplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for ShadowAi Hilt dependency injection.
 *
 * Applies Hilt plugin and adds standard Hilt dependencies.
 */
class ShadowAiHiltPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Apply Hilt plugin
        pluginManager.apply("com.google.dagger.hilt.android")
        pluginManager.apply("com.google.devtools.ksp")

        // Add Hilt dependencies
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            implementation(libs.findLibrary("hilt-android").get())
            ksp(libs.findLibrary("hilt-compiler").get())
        }
    }
}
