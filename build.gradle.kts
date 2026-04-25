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
        ktlint(libs.versions.ktlintTool.get()).editorConfigOverride(mapOf(
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
        ktlint(libs.versions.ktlintTool.get())
    }
    json {
        target("*.json", "*/src/**/*.json")
        targetExclude("**/build/**")
        gson()
    }
}

val libraryTargetSdk = 36
val isWindowsHost = System.getProperty("os.name")
    ?.contains("Windows", ignoreCase = true) == true

// tasks.named("check").configure {
//     dependsOn("spotlessCheck")
// }

// tasks.register("ciCheck") {
//     group = "verification"
//     description = "Full CI check including formatting verification"
//     dependsOn("spotlessCheck", "check")
// }

subprojects {
    if (isWindowsHost) {
        tasks.matching {
            it.name.startsWith("bundleLib") && it.name.contains("ToJar")
        }.configureEach {
            doNotTrackState(
                "Windows file locking can make AGP intermediate JAR outputs transiently unreadable."
            )
        }

        tasks.matching { it.name.contains("merge") && it.name.endsWith("NativeLibs") }.configureEach {
            doNotTrackState(
                "Windows file locking can make native merge intermediates transiently unreadable."
            )
        }
    }

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
