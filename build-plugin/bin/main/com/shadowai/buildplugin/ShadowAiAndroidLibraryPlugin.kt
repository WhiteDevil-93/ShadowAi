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
 * Convention plugin for ShadowAi Android library modules.
 *
 * Applies common configuration:
 * - compileSdk = 36
 * - minSdk = 24
 * - Java 17 compatibility
 * - Kotlin JVM toolchain 17
 * - Coroutines dependencies
 */
class ShadowAiAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Apply base plugins
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        // Configure Android extension
        extensions.configure<LibraryExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 24
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                }
            }

            lint {
                targetSdk = 36
            }
        }

        // Configure Kotlin JVM toolchain
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(17)
        }

        // Add standard dependencies
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            // Coroutines
            implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            implementation(libs.findLibrary("kotlinx-coroutines-android").get())

            // Testing
            testImplementation(libs.findLibrary("junit").get())
            testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
            androidTestImplementation(libs.findLibrary("androidx-junit").get())
            androidTestImplementation(libs.findLibrary("androidx-espresso-core").get())
        }
    }
}
