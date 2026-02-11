import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

fun deleteDirectoryWithRetry(path: Path, attempts: Int = 15, delayMs: Long = 120L) {
    repeat(attempts) { attempt ->
        try {
            if (!Files.exists(path)) return
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { entry ->
                    Files.deleteIfExists(entry)
                }
            }
            return
        } catch (e: IOException) {
            if (attempt == attempts - 1) throw e
            Thread.sleep(delayMs)
        }
    }
}

// NOTE: Avoid deleting KSP outputs before KSP tasks run.
// This previously caused intermittent compiler failures due missing generated sources.
fun String.escapeForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val gcpProjectId = ((project.findProperty("GCP_PROJECT_ID") as String?) ?: "").escapeForBuildConfig()
val gcpRegion = ((project.findProperty("GCP_REGION") as String?) ?: "us-central1").escapeForBuildConfig()
val gcpFunctionsRegion = ((project.findProperty("GCP_FUNCTIONS_REGION") as String?) ?: gcpRegion).escapeForBuildConfig()
val useIsolatedInferenceEngine = (
    project.findProperty("USE_ISOLATED_INFERENCE_ENGINE") as String?
)?.toBooleanStrictOrNull() ?: true

android {
    namespace = "com.shadowai.app"
    compileSdk = 36
    ndkVersion = "27.0.12077973" // Added to resolve missing NDK component issue as per quick-fix suggestion
    val releaseStoreFile = project.findProperty("RELEASE_STORE_FILE") as String?
    val releaseStorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
    val releaseKeyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
    val releaseKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
    val releaseSigningConfigured = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "com.shadowai.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        buildConfigField("String", "GCP_PROJECT_ID", "\"$gcpProjectId\"")
        buildConfigField("String", "GCP_REGION", "\"$gcpRegion\"")
        buildConfigField("String", "GCP_FUNCTIONS_REGION", "\"$gcpFunctionsRegion\"")
        buildConfigField(
            "boolean",
            "USE_ISOLATED_INFERENCE_ENGINE",
            useIsolatedInferenceEngine.toString()
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Enable native compilation for both debug and release builds
        // This ensures the rebuilt library (without OpenMP) is used
        externalNativeBuild {
            cmake {
                arguments(
                    // NDK 27 + c++_shared requires explicit STL configuration
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_OPENMP=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DANDROID_STL_FORCE_FEATURES=OFF"  // Reduce bloat, faster build
                )
                abiFilters("arm64-v8a")
                // CRITICAL: C++ flags for std::stringstream and other STL features
                cppFlags(
                    "-std=c++17",
                    "-frtti",
                    "-fexceptions"
                )
            }
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                // All values are guaranteed non-null by releaseSigningConfigured check
                storeFile = releaseStoreFile?.let { file(it) }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true  // Enable code shrinking and obfuscation
            isShrinkResources = true  // Remove unused resources
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions block was removed to resolve "Unresolved reference" and "kotlin extension" conflicts
    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Configure jniLibs source set - libllama_jni.so is built from CMake, not prebuilt
    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // Native build for llama.cpp local inference
    // Enabled for both debug and release builds to ensure rebuilt library is used
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            // Prevent stripping of native library symbols to resolve build errors
            keepDebugSymbols.add("**/libsqlcipher.so")
            keepDebugSymbols.add("**/libllama_jni.so")
            // CRITICAL: Bundle libc++_shared.so for native library compatibility
            // NDK 27 + c++_shared requires explicit packaging
            pickFirsts.add("**/libc++_shared.so")
            pickFirsts.add("**/libomp.so")  // OpenMP runtime (disabled but may be linked)
        }
        // Ensure shared STL is bundled - required for c++_shared NDK builds
        // Without this, libllama_jni.so fails to load with missing symbols
        dex {
            useLegacyPackaging = true
        }
    }

    lint {
        // Enable custom lint checks from ui-validator
        checkDependencies = true
        abortOnError = false
        lintConfig = file("lint.xml") // Optional: create a lint.xml for custom configuration
    }
}

dependencies {
    // Core Android libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.material)
    // MIGRATION: Removed deprecated play-services-auth - now using androidx.credentials
    implementation(libs.findbugs.jsr305)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose - Core
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.kotlinx.serialization.json)

    // Compose Integration
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation 3
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Hilt Navigation Compose
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Document access (SAF)
    implementation(libs.androidx.documentfile)

    // Compose - Tooling (debug only)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Compose - Testing
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Coil for Compose
    implementation(libs.coil.compose)

    // Hilt - Migrated to KSP
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Security & Storage
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.tink.android)
    implementation(libs.androidx.security.crypto)

    // Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Misc
    implementation(libs.snakeyaml)

    // ShadowAi Modules
    implementation(project(":core-contracts"))
    implementation(project(":model-catalog"))
    implementation(project(":provider-adapters"))
    implementation(project(":artifact-system"))
    implementation(project(":pipeline-planner"))
    implementation(project(":ui-params"))
    implementation(project(":diagnostics"))
    implementation(project(":hot-swapping"))
    implementation(project(":inference_process"))

    // UI Validator (Compliance Check)
    implementation(project(":ui-validator"))
    lintChecks(project(":ui-validator"))
}
