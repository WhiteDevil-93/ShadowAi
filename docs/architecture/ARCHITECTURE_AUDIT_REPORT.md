# ShadowAi Android - Architecture Audit Report

**Date:** 2026-02-08  
**Auditor:** SystemArchitect-P1  
**Project:** ShadowAi-Android-16  
**Scope:** Complete codebase architecture analysis

---

## Executive Summary

ShadowAi is a multi-modular Android application with 12 modules implementing an AI assistant with multimodal capabilities (text, image, video, audio). The architecture uses **MVVM pattern** with **Jetpack Compose**, **Hilt DI**, and a custom **Pipeline/Graph-based transformation system** for AI operations.

### Overall Grade: B-
- Good modular separation and clean architecture
- Custom lint rules and module boundary enforcement
- Critical issues found: Duplicate enums, minor inconsistencies

---

## 1. Module Structure (12 Modules)

| Module | Type | Purpose | Dependencies |
|--------|------|---------|--------------|
| `:app` | Android App | Main application entry | `:core-contracts`, `:model-catalog`, `:provider-adapters`, `:artifact-system`, `:pipeline-planner`, `:ui-params`, `:diagnostics`, `:hot-swapping` |
| `:backend` | Kotlin/JVM | Ktor server (separate) | (none) |
| `:core-contracts` | Android Library | Foundation interfaces/contracts | (none) |
| `:model-catalog` | Android Library | Model discovery & registry | `:core-contracts` |
| `:provider-adapters` | Android Library | AI provider implementations | `:core-contracts`, `:model-catalog` |
| `:artifact-system` | Android Library | Type-safe I/O wrapper | `:core-contracts` |
| `:pipeline-planner` | Android Library | Graph search for transforms | `:core-contracts`, `:provider-adapters`, `:artifact-system` |
| `:ui-params` | Android Library | Parameter rendering | `:core-contracts` |
| `:ui-composition` | Android Library | Compose UI components | `:core-contracts`, `:ui-params`, `:pipeline-planner`, `:artifact-system` |
| `:diagnostics` | Android Library | Error handling/debug | `:core-contracts`, `:pipeline-planner` |
| `:hot-swapping` | Android Library | Provider config hot-swap | `:core-contracts`, `:provider-adapters` |
| `:ui-validator` | Android Library | Custom lint rules | (lint-only, no core-contracts dep) |

---

## 2. Dependency Graph

```
                    ┌─────────────┐
                    │    :app     │
                    └──────┬──────┘
                           │
        ┌──────────┬───────┼───────┬──────────┐
        │          │       │       │          │
        ▼          ▼       ▼       ▼          ▼
┌──────────┐ ┌────────┐ ┌──────┐ ┌──────┐ ┌──────────┐
│:ui-params│ │:hot-   │ │:diag-│ │:model│ │:provider-│
│          │ │swapping│ │nostics│ │-catalog│ │adapters  │
└────┬─────┘ └────┬───┘ └────┬─┘ └──┬───┘ └─────┬────┘
     │            │          │      │           │
     │            │          │      └───────────┘
     │            │          │                  │
     │            │          │                  ▼
     │            │          │           ┌──────────┐
     │            │          │           │:core-    │
     │            │          │           │contracts │
     │            │          │           └──────────┘
     │            │          │                  ▲
     │            │          │                  │
     └────────────┴──────────┴──────────────────┘
                          │
                    ┌─────┴─────┐
                    │:artifact- │
                    │  system   │
                    └─────┬─────┘
                          │
                    ┌─────┴─────┐
                    │:pipeline- │
                    │  planner  │
                    └─────┬─────┘
                          │
                    ┌─────┴─────┐
                    │:ui-compo- │
                    │ sition    │
                    └───────────┘

┌─────────────┐
│  :backend   │ (Standalone JVM/Ktor module)
└─────────────┘

┌─────────────┐
│:ui-validator│ (Lint rules only)
└─────────────┘
```

---

## 3. Architecture Pattern

### 3.1 Primary Pattern: MVVM (Model-View-ViewModel)
- **UI Layer:** Jetpack Compose with StateFlow
- **ViewModel:** Hilt-injected, lifecycle-aware
- **Model:** Repository pattern for data operations

### 3.2 AI Pipeline Pattern (Custom)
- **Modality Sealed Class:** Text, Image, Video, Audio, Mixed
- **Transform Sealed Class:** Defines transformations (TextToImage, ImageToText, etc.)
- **PipelinePlanner:** Graph search algorithm to find optimal transformation paths
- **ProviderExecutor:** Interface for executing transforms
- **Artifact:** Type-safe wrapper for all I/O in the system

### 3.3 Dependency Injection
- **Framework:** Hilt with KSP (migrated from KAPT)
- **Scope:** Singleton for repositories, ViewModel for ViewModels

---

## 4. Critical Issues Found

### 4.1 DUPLICATE ENUMS - SEVERITY: HIGH
**Issue:** Two `ProviderId` enums exist with different values:

| Enum | Location | Unique Values |
|------|----------|---------------|
| `com.shadowai.core.ProviderId` | `:core-contracts` | FLUX, REPLICATE |
| `com.shadowai.app.providers.ProviderId` | `:app` | OLLAMA_CLOUD |

**Impact:**
- Serialization/deserialization errors when crossing module boundaries
- Confusion about which enum to use
- Missing `OLLAMA_CLOUD` in core-contracts prevents provider adapters from supporting it
- FLUX and REPLICATE exist in core but not in app

**Recommendation:** Consolidate to single source of truth in `:core-contracts`

### 4.2 DUPLICATE CAPABILITY ENUMS - SEVERITY: MEDIUM
**Issue:** Two `Capability` enums exist:
- `com.shadowai.core.Capability` - Full set (11 values)
- `com.shadowai.app.providers.Capability` - Subset (5 values: TEXT, VISION, IMAGE_GEN, FUNCTION_CALLS, VOICE)

**Impact:** Missing capabilities in app (STREAMING, SYSTEM_PROMPT, JSON_MODE, TOOL_USE, MULTIMODAL)

### 4.3 Missing Module Dependency - SEVERITY: MEDIUM
**Issue:** `:ui-validator` module does NOT depend on `:core-contracts`:
```kotlin
// Current dependencies
dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.13.1")
    // ... no implementation(project(":core-contracts"))
}
```

Despite this, the module boundary rules require all Android modules to depend on `:core-contracts`.

### 4.4 Compose Compiler Version Inconsistency - SEVERITY: LOW
- `:ui-composition` uses `kotlinCompilerExtensionVersion = "1.5.3"`
- Other Compose modules use Kotlin 2.3.0's compose plugin
- Root build uses `alias(libs.plugins.jetbrains.kotlin.compose)`

### 4.5 Incomplete Allowed Dependencies Map - SEVERITY: LOW
Module boundary rules don't include all modules:
```kotlin
val allowed = mapOf(
    // Missing: :ui-validator, :ui-composition
    // Partially defined but incomplete
)
```

---

## 5. Missing Module Implementations

| Expected | Status | Notes |
|----------|--------|-------|
| `:ui-composition` to depend on `:diagnostics` | Missing | Would allow error reporting in UI components |
| `:app` to use `:ui-composition` | Missing | App uses its own UI, not the library |
| `:ui-validator` integration | Partial | Lint rules exist but not enforcing core-contracts dependency |

---

## 6. Architectural Anti-Patterns

### 6.1 Leaky Abstraction (Minor)
`ChatViewModel` in `:app` references `AgentResult` classes instead of using the artifact system's type-safe wrappers.

### 6.2 Feature Envy (Minor)
`:app` module contains business logic that could be in feature modules (e.g., `ShadowAgent`, `TaskExecutor` could be in a separate `:agent-core` module).

### 6.3 God Object Prevention (Good)
The `:app` module's `ShadowAgent` is intentionally kept focused on orchestration, with actual execution delegated to `TaskExecutor`.

---

## 7. Sync/Build Blockers

| Issue | Likelihood | Workaround |
|-------|------------|------------|
| Duplicate ProviderId enums in same app | High | Use fully qualified imports or rename |
| Lint API version mismatch | Medium | Version 31.13.1 may need AGP alignment |
| `:ui-validator` missing android block | Low | Add `kotlin { jvmToolchain(17) }` block |

---

## 8. Custom Build Infrastructure

### 8.1 Module Boundaries Plugin (`buildSrc`)
Custom Gradle plugin that enforces:
1. All Android modules must depend on `:core-contracts`
2. UI modules cannot depend on provider implementations
3. Provider modules cannot depend on UI modules
4. Circular dependency detection
5. Allowed dependency whitelist

### 8.2 UI Validator Module
Custom lint rules for:
- Material3 compliance (color roles, elevation, typography)
- Window size class compliance
- Accessibility semantics
- System insets handling
- Visual hierarchy rules
- Mode separation (dark/light)

---

## 9. Technology Stack

| Category | Technology | Version |
|----------|------------|---------|
| Android | Compile SDK | 36 |
| Kotlin | Language | 2.3.0 |
| Gradle | AGP | 8.13.2 |
| Compose | BOM | 2024.12.01 |
| DI | Hilt | 2.58 |
| DB | Room | 2.8.4 |
| Network | Ktor | 3.4.0 |
| Backend | Ktor Server | 3.4.0 |

---

## 10. Recommendations

### Immediate (Critical)
1. **Consolidate ProviderId enums** - Remove duplicate from `:app`, use `:core-contracts` version exclusively
2. **Add OLLAMA_CLOUD to core-contracts** if it's a legitimate provider
3. **Update module boundaries** to include `:ui-validator` and `:ui-composition`

### Short-term (High Priority)
1. **Remove duplicate Capability enum** from `:app`
2. **Fix `:ui-validator` build.gradle.kts** - add standard `kotlin { jvmToolchain(17) }` block
3. **Unify Compose compiler versions** across all modules
4. **Add `:app` dependency on `:ui-composition`** to use shared UI components

### Long-term (Improvements)
1. Consider extracting `:agent-core` module from `:app`
2. Add integration tests for provider adapter mappings
3. Document the Pipeline/Graph architecture pattern

---

## Conclusion

The ShadowAi architecture demonstrates good modular design with clean separation of concerns. The custom build tooling (module boundaries plugin, ui-validator) shows mature engineering practices. However, the **duplicate ProviderId enums** are a critical issue that must be resolved before production deployment to prevent runtime crashes when the AI providers are used.

---

**Report Generated:** 2026-02-08  
**Next Review:** Recommended after ProviderId consolidation
