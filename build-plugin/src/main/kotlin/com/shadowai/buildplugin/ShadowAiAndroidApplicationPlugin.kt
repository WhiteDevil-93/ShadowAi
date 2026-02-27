package com.shadowai.buildplugin

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention plugin for ShadowAi Android application module.
 *
 * Provides shared configuration for the app module.
 * Note: The app module has extensive custom configuration,
 * so this is a lightweight convention that can be extended.
 */
class ShadowAiAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Apply base plugins
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        // Configure Android extension
        extensions.configure<ApplicationExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 24
                targetSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }

            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                }
            }

            buildFeatures {
                buildConfig = true
                compose = true
            }
        }

        // Configure Kotlin JVM toolchain
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(21)
        }
    }
}
