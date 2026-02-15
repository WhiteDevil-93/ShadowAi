plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.hilt.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.shadowai.inference"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.add("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags.addAll(
                    listOf(
                        "-O3",
                        "-DANDROID_STL=c++_shared",
                        "-DGGML_OPENMP=OFF",
                        "-DLLAMA_OPENMP=OFF",
                        "-DLLAMA_BUILD_SERVER=OFF"
                    )
                )
            }
        }
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            // Build isolated-process JNI entrypoints from this module's CMake,
            // not the app module, to keep symbol names aligned with NativeBridge.
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "27.0.12077973"
}

dependencies {
    implementation(project(":core-contracts"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt (minimal - service doesn't need much DI)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
