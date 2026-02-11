# ShadowAi Convention Plugins Setup

## Overview

This document describes the custom Gradle convention plugins created for the ShadowAi multi-module Android project. The convention plugins follow the **Androidify pattern** to centralize build configuration and reduce boilerplate across 11+ modules.

## Background

**Before:** Each module had ~50 lines of duplicated configuration:
- `compileSdk = 36`, `minSdk = 24`
- Java 17 compatibility blocks
- Kotlin JVM toolchain 17
- Coroutine dependencies
- Testing setup

**After:** Each module uses 1-3 lines of plugin declarations, with common configuration applied automatically.

---

## Architecture

```
ShadowAi/
├── build-plugin/           # Convention plugin module (included build)
│   ├── build.gradle.kts    # Plugin definitions
│   ├── settings.gradle.kts # Isolated settings for composite build
│   └── src/main/kotlin/com/shadowai/buildplugin/
│       ├── ShadowAiAndroidLibraryPlugin.kt         # Base library plugin
│       ├── ShadowAiAndroidLibraryComposePlugin.kt  # Library + Compose
│       ├── ShadowAiAndroidApplicationPlugin.kt     # Application plugin
│       ├── ShadowAiHiltPlugin.kt                   # Hilt DI plugin
│       ├── ShadowAiLintPlugin.kt                   # Lint check dev plugin
│       └── extensions.kt                           # Helper functions
│
├── settings.gradle.kts     # Root settings with pluginManagement
├── gradle/
│   └── libs.versions.toml  # Version catalog + plugin aliases
│
├── app/                    # Uses: shadowai.android.application + compose + hilt
├── core-contracts/         # Uses: shadowai.android.library
├── model-catalog/          # Uses: shadowai.android.library
├── provider-adapters/      # Uses: shadowai.android.library + hilt
├── artifact-system/        # Uses: shadowai.android.library
├── pipeline-planner/       # Uses: shadowai.android.library
├── ui-params/              # Uses: shadowai.android.library.compose
├── ui-composition/         # Uses: shadowai.android.library.compose
├── diagnostics/            # Uses: shadowai.android.library.compose
├── hot-swapping/           # Uses: shadowai.android.library
└── ui-validator/           # Uses: shadowai.lint
```

---

## Convention Plugins

### 1. `shadowai.android.library` (Base Library)

**Purpose:** Standard configuration for all Android library modules.

**Applied Configuration:**
```kotlin
plugins: com.android.library + org.jetbrains.kotlin.android

android {
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { targetSdk = 36 }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(kotlinx-coroutines-core)
    implementation(kotlinx-coroutines-android)
    testImplementation(junit)
    testImplementation(kotlinx-coroutines-test)
    androidTestImplementation(androidx-junit)
    androidTestImplementation(androidx-espresso-core)
}
```

**Modules Using:**
- `core-contracts`
- `model-catalog`
- `artifact-system`
- `pipeline-planner`
- `hot-swapping`

---

### 2. `shadowai.android.library.compose` (Library + Compose)

**Purpose:** Library modules that use Jetpack Compose.

**Extends:** `shadowai.android.library`

**Additional Configuration:**
```kotlin
plugins: org.jetbrains.kotlin.plugin.compose

android.buildFeatures { compose = true }

dependencies {
    implementation(platform(compose-bom))
    implementation(compose-ui, compose-material3, compose-foundation, compose-animation)
    implementation(kotlinx-serialization-json)
    debugImplementation(compose-ui-tooling, compose-test-manifest)
    androidTestImplementation(compose-ui-test-junit4)
}
```

**Modules Using:**
- `ui-params`
- `ui-composition`
- `diagnostics`

---

### 3. `shadowai.android.application` (Application)

**Purpose:** Configuration for the main Android application module.

**Configuration:**
```kotlin
plugins: com.android.application + org.jetbrains.kotlin.android

android {
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true, compose = true }
}

kotlin { jvmToolchain(17) }
```

**Note:** The `:app` module has extensive custom configuration (Firebase, native build, signing, packaging, etc.) that is kept in its own `build.gradle.kts` for clarity. The convention plugin provides the base.

---

### 4. `shadowai.hilt` (Dependency Injection)

**Purpose:** Hilt setup for modules requiring dependency injection.

**Configuration:**
```kotlin
plugins: com.google.dagger.hilt.android + com.google.devtools.ksp

dependencies {
    implementation(hilt-android)
    ksp(hilt-compiler)
}
```

**Modules Using:**
- `provider-adapters`
- `app` (via direct plugin application)

---

### 5. `shadowai.lint` (Custom Lint Development)

**Purpose:** Setup for developing custom lint checks.

**Extends:** `shadowai.android.library`

**Additional Configuration:**
```kotlin
plugins: com.google.devtools.ksp

android.lint { disable += "all" } // Don't lint the lint module

dependencies {
    compileOnly(lint-api, lint-checks)
    implementation(auto-service-annotations)
    ksp(auto-service-ksp)
    testImplementation(junit, lint-tests)
    compileOnly(javax.annotation-api)
    implementation(gson)
    // Compose for analysis
    implementation(compose-ui, compose-material3, compose-foundation, window-size)
}
```

**Modules Using:**
- `ui-validator`

---

## Migration Summary

### Module Migrations Completed

| Module | Before | After |
|--------|--------|-------|
| `core-contracts` | 30 lines manual | 6 lines (`shadowai.android.library`) |
| `model-catalog` | 35 lines manual | 11 lines (`shadowai.android.library`) |
| `provider-adapters` | 43 lines manual | 16 lines (`shadowai.android.library` + `shadowai.hilt`) |
| `artifact-system` | 30 lines manual | 6 lines (`shadowai.android.library`) |
| `pipeline-planner` | 33 lines manual | 10 lines (`shadowai.android.library`) |
| `ui-params` | 35 lines manual | 6 lines (`shadowai.android.library.compose`) |
| `ui-composition` | 38 lines manual | 12 lines (`shadowai.android.library.compose`) |
| `diagnostics` | 35 lines manual | 8 lines (`shadowai.android.library.compose`) |
| `hot-swapping` | 33 lines manual | 12 lines (`shadowai.android.library`) |
| `ui-validator` | 85 lines manual | 54 lines (`shadowai.lint`) |

**Total Lines of Build Configuration:**
- Before: ~400+ lines
- After: ~140 lines (65% reduction)

---

## Standard Configuration Applied

### Android SDK
| Setting | Value |
|---------|-------|
| Compile SDK | 36 |
| Min SDK | 24 |
| Target SDK | 36 (libraries), 36 (app) |
| Java Compatibility | VERSION_17 |
| Kotlin JVM Toolchain | 17 |

### Standard Dependencies (per module type)

**All Library Modules:**
- `kotlinx-coroutines-core`
- `kotlinx-coroutines-android`
- `junit` (test)
- `kotlinx-coroutines-test` (test)
- `androidx-junit` (androidTest)
- `androidx-espresso-core` (androidTest)

**Compose Modules (additional):**
- `compose-bom` (platform)
- `compose-ui`, `compose-material3`, `compose-foundation`, `compose-animation`
- `kotlinx-serialization-json`
- `compose-ui-tooling` (debug)
- `compose-ui-test-junit4` (androidTest)

**Hilt Modules (additional):**
- `hilt-android`
- `hilt-compiler` (ksp)

---

## Usage Examples

### Basic Library Module
```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.shadowai.android.library)
}

android {
    namespace = "com.shadowai.mymodule"
}

dependencies {
    implementation(project(":core-contracts"))
    // Add module-specific deps here
}
```

### Library with Compose
```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.shadowai.android.library.compose)
}

android {
    namespace = "com.shadowai.myuimodule"
}

dependencies {
    implementation(project(":core-contracts"))
    // Compose deps automatically included
    implementation(libs.coil.compose) // Add extras as needed
}
```

### Library with Hilt
```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.shadowai.android.library)
    alias(libs.plugins.shadowai.hilt)
}

android {
    namespace = "com.shadowai.myservice"
}

dependencies {
    implementation(project(":core-contracts"))
    // Hilt deps automatically included
}
```

---

## Plugin Development

### Adding New Convention Plugins

1. Create plugin class in `build-plugin/src/main/kotlin/com/shadowai/buildplugin/`:
```kotlin
class MyNewPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Configure here
    }
}
```

2. Register in `build-plugin/build.gradle.kts`:
```kotlin
gradlePlugin {
    plugins {
        create("myNewPlugin") {
            id = "shadowai.my.new.plugin"
            implementationClass = "com.shadowai.buildplugin.MyNewPlugin"
        }
    }
}
```

3. Add alias to `gradle/libs.versions.toml`:
```toml
[plugins]
shadowai-my-new-plugin = { id = "shadowai.my.new.plugin", version = "0.1.0" }
```

4. Sync and use:
```kotlin
plugins {
    alias(libs.plugins.shadowai.my.new.plugin)
}
```

---

## Future Enhancements

### Potential New Convention Plugins

1. **`shadowai.network`** - For modules needing Retrofit/OkHttp
   - Adds networking stack dependencies
   - Common interceptors configuration

2. **`shadowai.room`** - For modules with local database
   - Room dependencies + ksp
   - Common database config

3. **`shadowai.firebase`** - For Firebase-enabled modules
   - Firebase BOM + individual services
   - Google services plugin

4. **`shadowai.testing`** - Extended testing setup
   - Mockito, MockK, coroutines-test
   - Test fixtures configuration

### Build Health Improvements

1. Enable **Spotless** for code formatting across all modules
2. Add **Detekt** for static analysis
3. Consider **Dependency Analysis** plugin to find unused deps
4. **Module graph assertion** plugin to enforce module boundaries

---

## Troubleshooting

### Plugin Not Found
```
Plugin [id: 'shadowai.android.library'] was not found
```
**Solution:** Ensure `includeBuild("build-plugin")` is in `settings.gradle.kts` pluginManagement block.

### Version Catalog Access
If plugins can't access `libs`:
**Solution:** Use `extensions.getByType<VersionCatalogsExtension>().named("libs")` in plugin code.

### Plugin Changes Not Applied
**Solution:** Run with `--refresh-dependencies` or clear `.gradle` caches.

---

## Files Modified/Created

### New Files
```
build-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/kotlin/com/shadowai/buildplugin/
    ├── ShadowAiAndroidLibraryPlugin.kt
    ├── ShadowAiAndroidLibraryComposePlugin.kt
    ├── ShadowAiAndroidApplicationPlugin.kt
    ├── ShadowAiHiltPlugin.kt
    ├── ShadowAiLintPlugin.kt
    └── extensions.kt
```

### Modified Files
```
settings.gradle.kts                  # Added pluginManagement includeBuild
gradle/libs.versions.toml            # Added plugin + library deps

core-contracts/build.gradle.kts      # Migrated to convention plugin
model-catalog/build.gradle.kts        # Migrated to convention plugin
provider-adapters/build.gradle.kts    # Migrated to convention plugin
artifact-system/build.gradle.kts      # Migrated to convention plugin
pipeline-planner/build.gradle.kts     # Migrated to convention plugin
ui-params/build.gradle.kts            # Migrated to convention plugin
ui-composition/build.gradle.kts       # Migrated to convention plugin
diagnostics/build.gradle.kts          # Migrated to convention plugin
hot-swapping/build.gradle.kts         # Migrated to convention plugin
ui-validator/build.gradle.kts         # Migrated to convention plugin
```

---

## Success Metrics

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Avg Module Config Lines | ~35 | ~10 | **71% reduction** |
| Duplicated SDK Config | 11 times | 1 time (plugin) | **91% reduction** |
| Duplicated Java/Kotlin Config | 11 times | 1 time (plugin) | **91% reduction** |
| Coroutines Declarations | 11 times | 1 time (plugin) | **91% reduction** |
| Test Setup Duplication | 11 times | 1 time (plugin) | **91% reduction** |
| Build File Maintainability | Low | High | Centralized updates |

---

## References

- [Gradle Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html)
- [Gradle Convention Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)
- [Androidify Gradle Pattern](https://github.com/android/nowinandroid/tree/main/build-logic)
- [Version Catalogs](https://docs.gradle.org/current/userguide/platforms.html)

---

**Report Generated:** 2026-02-09
**Author:** ShadowAi Build System
**Status:** ✅ Completed & Applied
