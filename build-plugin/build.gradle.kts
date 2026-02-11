plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        // Android Library Convention Plugin
        create("shadowaiAndroidLibrary") {
            id = "shadowai.android.library"
            implementationClass = "com.shadowai.buildplugin.ShadowAiAndroidLibraryPlugin"
        }
        // Android Library with Compose Convention Plugin
        create("shadowaiAndroidLibraryCompose") {
            id = "shadowai.android.library.compose"
            implementationClass = "com.shadowai.buildplugin.ShadowAiAndroidLibraryComposePlugin"
        }
        // Android Application Convention Plugin
        create("shadowaiAndroidApplication") {
            id = "shadowai.android.application"
            implementationClass = "com.shadowai.buildplugin.ShadowAiAndroidApplicationPlugin"
        }
        // Hilt Convention Plugin
        create("shadowaiHilt") {
            id = "shadowai.hilt"
            implementationClass = "com.shadowai.buildplugin.ShadowAiHiltPlugin"
        }
        // Lint/Library Validator Convention Plugin
        create("shadowaiLint") {
            id = "shadowai.lint"
            implementationClass = "com.shadowai.buildplugin.ShadowAiLintPlugin"
        }
    }
}

dependencies {
    // Android Gradle Plugin
    implementation(libs.android.gradlePlugin)
    // Kotlin Gradle Plugin
    implementation(libs.kotlin.gradlePlugin)
    // KSP Gradle Plugin
    implementation(libs.ksp.gradlePlugin)
    // Kotlin Compose Compiler Plugin (required for library.compose plugin)
    implementation(libs.kotlin.compose.compilerPlugin)
    // Hilt Gradle Plugin (required for hilt plugin)
    implementation(libs.hilt.gradlePlugin.artifact)
    // Ktlint Gradle Plugin
    implementation(libs.ktlint.gradlePlugin.artifact)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
