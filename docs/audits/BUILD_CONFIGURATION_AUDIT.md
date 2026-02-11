# ShadowAi Build Configuration Audit

**Date:** 2026-02-09  
**Auditor:** BuildAudit-P1  
**Scope:** All 14 module build files, settings.gradle.kts, and gradle/libs.versions.toml

---

## Executive Summary

**Status:** ⚠️ **15 DISCREPANCIES FOUND**

The build configuration is largely consistent but contains several discrepancies that should be addressed for maintainability and correctness.

---

## 1. PLUGIN VERSION CONSISTENCY

### ✅ All modules using version catalog aliases

All modules correctly use `alias(libs.plugins.xxx)` syntax. No hardcoded plugin versions found.

### ❌ DISCREPANCY: Root project applies `shadowai.module-boundaries` differently

**File:** `build.gradle.kts` (root)  
**Issue:** The root project uses `id("shadowai.module-boundaries")` without version catalog alias

**Current:**
```kotlin
id("shadowai.module-boundaries")
```

**Suggested Fix:** Add to version catalog and use alias:
```toml
[plugins]
shadowai-module-boundaries = { id = "shadowai.module-boundaries", version = "unspecified" }
```

```kotlin
alias(libs.plugins.shadowai.module.boundaries)
```

---

## 2. KOTLIN VERSION ALIGNMENT (1.9.25)

### ✅ All modules aligned

| Module | Plugin | Version Source |
|--------|--------|----------------|
| Root | jetbrains-kotlin-android | libs.plugins |
| app | jetbrains-kotlin-android | libs.plugins |
| backend | kotlin("jvm") | libs.plugins |
| core-contracts | jetbrains-kotlin-android | libs.plugins |
| model-catalog | jetbrains-kotlin-android | libs.plugins |
| provider-adapters | jetbrains-kotlin-android | libs.plugins |
| artifact-system | jetbrains-kotlin-android | libs.plugins |
| pipeline-planner | jetbrains-kotlin-android | libs.plugins |
| ui-params | jetbrains-kotlin-android | libs.plugins |
| ui-composition | jetbrains-kotlin-android | libs.plugins |
| ui-validator | jetbrains-kotlin-android | libs.plugins |
| diagnostics | jetbrains-kotlin-android | libs.plugins |
| hot-swapping | jetbrains-kotlin-android | libs.plugins |

**Version catalog:** `kotlin = "1.9.25"` ✅

**Note:** The backend module uses `kotlin("jvm")` instead of the catalog alias `jetbrainsKotlinJvm`, but both resolve to 1.9.25.

---

## 3. COMPOSE COMPILER VERSION

### ❌ DISCREPANCY: Commented-out Compose compiler plugin references

**Files affected:**
- `app/build.gradle.kts` (line ~12)
- `ui-params/build.gradle.kts`
- `ui-composition/build.gradle.kts`
- `diagnostics/build.gradle.kts`

**Issue:** Comments state "Compose compiler plugin requires Kotlin 2.0+" and "Removed alias(libs.plugins.jetbrains.kotlin.compose)", but for Kotlin 1.9.25, AGP 8.13.2 handles Compose compiler automatically.

**Current:**
```kotlin
// Compose compiler plugin requires Kotlin 2.0+
// For Kotlin 1.9.25, Compose is handled via Android Gradle Plugin
// Removed alias(libs.plugins.jetbrains.kotlin.compose)
```

**Suggested Fix:** Remove the outdated comments. The configuration is correct for Kotlin 1.9.25 + AGP 8.13.2, but the comments add confusion.

### ❌ DISCREPANCY: Missing explicit compose compiler version for BOM 2024.02.02

**Issue:** The Compose BOM 2024.02.02 was designed for Compose Compiler 1.5.10. With Kotlin 1.9.25, the compatible Compose Compiler version is 1.5.15 (bundled with AGP 8.13.2).

**Suggested Fix:** Add explicit Compose Compiler configuration in `app/build.gradle.kts`:
```kotlin
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.15"
}
```

Or upgrade to a newer BOM that includes Compose Compiler 1.5.15:
```toml
composeBom = "2024.05.00"  # Includes Compose Compiler 1.5.15
```

---

## 4. DEPENDENCY VERSION ALIGNMENT

### ❌ DISCREPANCY: `kotlin("reflect")` uses unversioned notation

**Files affected:**
- `model-catalog/build.gradle.kts`
- `pipeline-planner/build.gradle.kts`

**Current:**
```kotlin
implementation(kotlin("reflect"))
```

**Issue:** Uses Kotlin platform default version instead of explicit version from catalog.

**Suggested Fix:** Add to version catalog:
```toml
[libraries]
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }
```

Then use:
```kotlin
implementation(libs.kotlin.reflect)
```

### ❌ DISCREPANCY: `buildSrc/build.gradle.kts` uses hardcoded repositories

**File:** `buildSrc/build.gradle.kts`

**Current:**
```kotlin
repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
```

**Issue:** Not using the version catalog plugin, but this is acceptable for buildSrc. However, it's inconsistent with other modules.

---

## 5. JVM TARGET CONSISTENCY

### ✅ All modules targeting JVM 17

| Module | Configuration | Status |
|--------|--------------|--------|
| app | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| backend | `kotlin { jvmToolchain(17) }` + `java {}` | ✅ |
| core-contracts | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| model-catalog | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| provider-adapters | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| artifact-system | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| pipeline-planner | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| ui-params | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| ui-composition | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| ui-validator | `kotlin { jvmToolchain(17) }` outside `android {}` | ⚠️ |
| diagnostics | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |
| hot-swapping | `kotlin { jvmToolchain(17) }` + `compileOptions` | ✅ |

### ❌ DISCREPANCY: `ui-validator` has `kotlin` block placement inconsistency

**File:** `ui-validator/build.gradle.kts`  
**Issue:** The `kotlin {}` block is outside the `android {}` block, unlike other Android library modules.

**Current:**
```kotlin
android {
    // ... config
}

kotlin {
    jvmToolchain(17)
}
```

**Suggested Fix:** Move inside `android {}` for consistency:
```kotlin
android {
    // ... config
    kotlin {
        jvmToolchain(17)
    }
}
```

---

## 6. INCONSISTENT CONFIGURATIONS

### ❌ DISCREPANCY: Backend module uses different plugin application pattern

**File:** `backend/build.gradle.kts`

**Current:**
```kotlin
plugins {
    kotlin("jvm")
    application
}
```

**Expected for consistency (optional):**
```kotlin
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    application
}
```

**Note:** This is technically fine but inconsistent with the rest of the codebase.

### ❌ DISCREPANCY: Namespace inconsistencies

| Module | Namespace | Pattern |
|--------|-----------|---------|
| app | `com.shadowai.app` | ✅ Standard |
| core-contracts | `com.shadowai.core` | ⚠️ Should be `com.shadowai.corecontracts`? |
| model-catalog | `com.shadowai.modelcatalog` | ✅ Standard |
| provider-adapters | `com.shadowai.provideradapters` | ✅ Standard |
| artifact-system | `com.shadowai.artifactsystem` | ✅ Standard |
| pipeline-planner | `com.shadowai.pipelineplanner` | ✅ Standard |
| ui-params | `com.shadowai.uiparams` | ✅ Standard |
| ui-composition | `com.shadowai.uicomposition` | ✅ Standard |
| ui-validator | `com.shadowai.uivalidator` | ✅ Standard |
| diagnostics | `com.shadowai.diagnostics` | ✅ Standard |
| hot-swapping | `com.shadowai.hotswapping` | ✅ Standard |
| backend | (JVM app, no namespace) | N/A |

**Suggested Fix:** Consider renaming core-contracts namespace to `com.shadowai.corecontracts` for consistency.

### ❌ DISCREPANCY: `hot-swapping` uses `api()` for module dependencies

**File:** `hot-swapping/build.gradle.kts`

**Current:**
```kotlin
dependencies {
    api(project(":core-contracts"))
    api(project(":provider-adapters"))
```

**Issue:** Other modules use `implementation()` for internal module dependencies. Using `api()` exposes these modules to consumers of hot-swapping.

**Suggested Fix:** Unless intentional API exposure is required, use `implementation()`:
```kotlin
dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":provider-adapters"))
```

---

## 7. MISSING DEPENDENCIES (Ollama Cloud Integration)

### ⚠️ NEEDS VERIFICATION: Ollama Cloud adapter dependencies

The audit scope mentions "New Ollama Cloud adapter dependencies verified", but after reviewing all build files, **no explicit Ollama Cloud dependencies were found** in:
- `provider-adapters/build.gradle.kts` (where they would logically be)
- Any Ktor client configurations for Ollama endpoints

**Expected dependencies that may be missing:**
```kotlin
// In provider-adapters/build.gradle.kts or new ollama module:
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.cio)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
```

**Action Required:** Verify if Ollama Cloud integration is:
1. Implemented in code but not visible in build files
2. Planned but not yet implemented
3. Implemented as part of existing provider-adapters using generic Ktor

---

## 8. KSP VERSION (1.9.25-1.0.20)

### ✅ KSP version correctly aligned

**Version catalog:** `ksp = "1.9.25-1.0.20"` ✅

**Modules using KSP:**
- `app/build.gradle.kts` - Room, Hilt compilers
- `ui-validator/build.gradle.kts` - auto-service-ksp

Both correctly reference the KSP plugin from the catalog.

---

## DISCREPANCY SUMMARY TABLE

| # | File | Issue | Severity |
|----|------|-------|----------|
| 1 | `build.gradle.kts` (root) | Plugin ID not using version catalog alias | Low |
| 2 | `app/build.gradle.kts` | Outdated Compose plugin comment | Low |
| 3 | `ui-params/build.gradle.kts` | Outdated Compose plugin comment | Low |
| 4 | `ui-composition/build.gradle.kts` | Outdated Compose plugin comment | Low |
| 5 | `diagnostics/build.gradle.kts` | Outdated Compose plugin comment | Low |
| 6 | `gradle/libs.versions.toml` | BOM 2024.02.02 may need Compose Compiler 1.5.15 | Medium |
| 7 | `model-catalog/build.gradle.kts` | `kotlin("reflect")` unversioned | Low |
| 8 | `pipeline-planner/build.gradle.kts` | `kotlin("reflect")` unversioned | Low |
| 9 | `ui-validator/build.gradle.kts` | `kotlin` block outside `android` block | Low |
| 10 | `backend/build.gradle.kts` | Uses `kotlin("jvm")` instead of alias | Low |
| 11 | `core-contracts/build.gradle.kts` | Namespace `core` may conflict with generic term | Low |
| 12 | `hot-swapping/build.gradle.kts` | Uses `api()` instead of `implementation()` | Medium |
| 13 | `provider-adapters/build.gradle.kts` | Verify Ollama Cloud dependencies | High |
| 14 | `buildSrc/build.gradle.kts` | Hardcoded repositories (acceptable) | Info |
| 15 | `app/build.gradle.kts` | Missing explicit compose compiler version | Medium |

---

## RECOMMENDED PRIORITY ACTIONS

### 🔴 High Priority
1. **Verify Ollama Cloud integration** - Ensure required dependencies are present
2. **Update Compose BOM or add explicit compiler version** - Prevent runtime issues

### 🟡 Medium Priority
3. **Fix `hot-swapping` module dependencies** - Use `implementation()` instead of `api()` unless intentional
4. **Add `kotlin-reflect` to version catalog** - Ensure version alignment

### 🟢 Low Priority
5. **Clean up outdated comments** - Remove misleading Compose plugin comments
6. **Fix `ui-validator` block structure** - Move `kotlin` inside `android`
7. **Consider namespace consistency** - Rename `core` to `corecontracts`

---

## VERIFICATION COMMANDS

```bash
# Verify all plugins resolve correctly
./gradlew tasks --all --dry-run

# Check for dependency resolution issues
./gradlew dependencies --configuration compileClasspath

# Verify KSP version
./gradlew buildEnvironment | grep ksp

# Check Kotlin version alignment
./gradlew buildEnvironment | grep kotlin
```

---

## CONCLUSION

The ShadowAi build configuration is **functionally correct** but has **15 minor discrepancies** that reduce maintainability and consistency. The most critical item is verifying Ollama Cloud integration dependencies. All other issues are code quality improvements.

**Risk Assessment:** Low - no breaking issues found. Recommended to address medium/low priority items in next refactoring session.
