import com.android.build.gradle.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.dependency.check) apply true
    alias(libs.plugins.spotless) apply true
    id("shadowai.module-boundaries")
}

spotless {
    kotlin {
        target("*/src/**/*.kt", "src/**/*.kt", "*.kts")
        targetExclude("**/build/**", "**/.gradle/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf(
            "indent_size" to "4",
            "indent_style" to "space",
            "max_line_length" to "off",
            "ktlint_standard_trailing-comma-on-call-site" to "enabled",
            "ktlint_standard_trailing-comma-on-declaration-site" to "enabled"
        ))
    }
    kotlinGradle {
        target("*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    json {
        target("*.json", "*/src/**/*.json")
        targetExclude("**/build/**")
        gson()
    }
}

val libraryTargetSdk = 36

tasks.named("check").configure {
    dependsOn("spotlessCheck")
}

tasks.register("ciCheck") {
    group = "verification"
    description = "Full CI check including formatting verification"
    dependsOn("spotlessCheck", "check")
}

subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" &&
                !requested.name.startsWith("kotlin-gradle-plugin")) {
                useVersion(libs.versions.kotlin.get())
            }
        }
    }

    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            testOptions {
                unitTests {
                    targetSdk = libraryTargetSdk
                }
            }
            lint {
                targetSdk = libraryTargetSdk
            }
        }
    }
}
