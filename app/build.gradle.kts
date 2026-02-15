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
    alias(libs.plugins.kover)
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
    buildToolsVersion = "36.1.0"
    ndkVersion = "27.0.12077973"
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
        versionName = "1.0.0"
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

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_OPENMP=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DANDROID_STL_FORCE_FEATURES=OFF"
                )
                abiFilters("arm64-v8a")
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
                storeFile = releaseStoreFile?.let { file(it) }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("**/libsqlcipher.so")
            keepDebugSymbols.add("**/libllama_jni.so")
            pickFirsts.add("**/libc++_shared.so")
            pickFirsts.add("**/libomp.so")
        }
        dex {
            useLegacyPackaging = true
        }
    }

    lint {
        checkDependencies = true
        abortOnError = false
        lintConfig = file("lint.xml")
    }
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.material)
    implementation(libs.findbugs.jsr305)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ADDED: Required for ProcessLifecycleOwner
    implementation(libs.androidx.lifecycle.process)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

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

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.documentfile)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.sqlcipher)
    implementation(libs.sqlite)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.tink.android)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.snakeyaml)

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation(project(":core-contracts"))
    implementation(project(":model-catalog"))
    implementation(project(":provider-adapters"))
    implementation(project(":artifact-system"))
    implementation(project(":pipeline-planner"))
    implementation(project(":ui-params"))
    implementation(project(":diagnostics"))
    implementation(project(":hot-swapping"))
    implementation(project(":inference_process"))

    implementation(project(":ui-validator"))
    lintChecks(project(":ui-validator"))
}
