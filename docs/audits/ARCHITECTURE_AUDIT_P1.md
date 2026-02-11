# ShadowAi Architecture Audit - Phase 1
**Date:** 2026-02-09  
**Auditor:** SubAgent (ArchAudit-P1)  
**Scope:** Module boundaries, dependency direction, ProviderId consistency, adapter patterns

---

## Summary

**Critical Issues Found:** 7  
**Moderate Issues Found:** 6  
**Minor Issues Found:** 4  

**Status:** ⚠️ Architecture has significant inconsistencies that need immediate attention

---

## 1. MODULE STRUCTURE DISCREPANCIES

### Issue 1.1: Module Count Mismatch (MODERATE)
**Documentation** (AGENTS.md Section 1) states:
```
app/
├── core-contracts/
├── model-catalog/
├── provider-adapters/
├── artifact-system/
├── pipeline-planner/
├── diagnostics/
├── hot-swapping/
└── backend/
```

**Actual modules** (settings.gradle.kts):
```kotlin
include(":app")
include(":backend")
include(":core-contracts")
include(":model-catalog")
include(":provider-adapters")
include(":artifact-system")
include(":pipeline-planner")
include(":ui-params")
include(":ui-composition")
include(":diagnostics")
include(":hot-swapping")
include(":ui-validator")
```

**Missing from documentation:**
- `ui-params` - Model parameter management
- `ui-composition` - View composition for AI transforms
- `ui-validator` - Lint rules and compliance checking

**Recommendation:** Update AGENTS.md documentation to reflect actual module count (12 modules total including app, not 8).

---

## 2. DEPENDENCY DIRECTION ISSUES

### Issue 2.1: hot-swapping Module Exposes Dependencies Improperly (MODERATE)
**File:** `hot-swapping/build.gradle.kts`

```kotlin
dependencies {
    api(project(":core-contracts"))      // Should be implementation?
    api(project(":provider-adapters"))   // SHOULD BE implementation
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    api(libs.snakeyaml)                   // Should be implementation?
}
```

**Problem:** Using `api` instead of `implementation` for `provider-adapters` and `snakeyaml` exposes these as transitive dependencies to all consumers. This breaks encapsulation and allows other modules to accidentally depend on these without explicit declaration.

**Recommendation:** Change to `implementation` unless there is a specific API-surface reason to expose these.

### Issue 2.2: ui-composition Depends on Too Many Modules (MINOR)
**File:** `ui-composition/build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":ui-params"))
    implementation(project(":pipeline-planner"))  // Questionable?
    implementation(project(":artifact-system"))   // Questionable?
    // ...
}
```

**Problem:** A UI composition module should ideally depend primarily on `core-contracts` and `ui-params`. Depending on `pipeline-planner` and `artifact-system` couples UI to business logic processing.

**Impact:** This may indicate UI layer bleed into data/business logic layer.

---

## 3. PUBLIC API CONSISTENCY ISSUES

### Issue 3.1: ProviderAdapter Pattern Inconsistency (CRITICAL)
**Pattern expected:** All adapters should implement `ProviderAdapter` with consistent structure.

**Inconsistencies found:**

| Adapter | init() Returns | uses `isInitialized` | Has getPriority() | Issue |
|---------|---------------|---------------------|-------------------|-------|
| LocalLlamaAdapter | Boolean | Yes | Yes | Uses reflection extensively |
| OllamaCloudAdapter | Boolean | Yes | Yes | ✅ Clean |
| OpenAICompatibleAdapter | Boolean | Yes | Yes | ✅ Clean |
| FluxAdapter | Boolean | Yes | Yes | Multi-mode complexity |
| ReplicateAdapter | Boolean | No | Yes | Missing init tracking |
| AnthropicAdapter | Boolean | No | Yes | Missing init tracking |
| GeminiAdapter | Boolean | No | Yes | Missing init tracking |
| PixAIAdapter | Boolean | No | Yes | Missing init tracking |
| NovitaAdapter | Boolean | No | Yes | Missing init tracking |
| NovelAIAdapter | Boolean | No | Yes | Missing init tracking |

**Specific issues:**
1. **LocalLlamaAdapter.java:56** - Uses reflection to call `LocalInferenceManager` methods instead of proper DI
2. **ReplicateAdapter, AnthropicAdapter, GeminiAdapter, PixAIAdapter, NovitaAdapter, NovelAIAdapter** - Don't track initialization state in `isInitialized` field

### Issue 3.2: LocalLlamaAdapter Relies on Reflection (CRITICAL)
**File:** `provider-adapters/src/main/kotlin/.../LocalLlamaAdapter.kt`

```kotlin
// Lines 56-90: Dangerous reflection usage
val loadModelMethod = inferenceManager::class.java.getMethod("loadModel", String::class.java)
val model = loadModelMethod.invoke(inferenceManager, config.baseUrl)
// ... more reflection to access internal classes
```

**Problems:**
1. Bypasses type safety
2. Will fail at runtime if `app` module changes
3. Breaks IDE refactoring support
4. Cannot be unit tested without mocking reflection
5. Violates the contract boundary between modules

**Recommendation:** Either:
- Move LocalInferenceManager interface to core-contracts
- Create a proper adapter interface in core-contracts
- Or merge LocalLlamaAdapter into app module

---

## 4. ProviderId ENUM SINGLE SOURCE OF TRUTH

### Issue 4.1: ProviderId Enum Location Correct (✅ CORRECT)
**Location:** `core-contracts/src/main/kotlin/com/shadowai/core/ProviderId.kt`

Single location, properly exported. ✅

### Issue 4.2: Missing Adapters for ProviderId Values (CRITICAL)
ProviderId enum has 23 values, but ProviderAdapterFactory only handles:
- LOCAL_TEXT, LOCAL_IMAGE, LIQUID → LocalLlamaAdapter
- OPENAI, OPENROUTER, GROQ, COHERE, SILICON_FLOW, MISTRAL, DEEPSEEK, XAI, ATLASCLOUD, SIRAY → OpenAICompatibleAdapter
- ANTHROPIC → AnthropicAdapter
- GEMINI → GeminiAdapter
- PIXAI → PixAIAdapter
- NOVITA → NovitaAdapter
- NOVELAI → NovelAIAdapter
- FLUX → FluxAdapter
- REPLICATE → ReplicateAdapter
- OLLAMA_CLOUD → OllamaCloudAdapter

**Missing/Unhandled ProviderIds:**
- HUGGING_FACE - No dedicated adapter (falls through to throw)
- AMAZON_BEDROCK - No adapter at all
- UNKNOWN - Explicitly thrown as error

**Evidence:**
```kotlin
// ProviderAdapterFactory.kt:79
else -> throw IllegalArgumentException("Unknown provider: ${config.providerId}")
```

**Recommendation:**
1. Add HuggingFaceAdapter or route HUGGING_FACE to OpenAICompatibleAdapter
2. Add AmazonBedrockAdapter implementation
3. Handle UNKNOWN with graceful degradation

### Issue 4.3: isLocal() Method Inconsistency (MODERATE)
**File:** `core-contracts/.../ProviderId.kt`

```kotlin
fun isLocal(): Boolean = this == LOCAL_IMAGE || this == LOCAL_TEXT || this == LIQUID || this == FLUX
```

**Problem:** FLUX is considered "local" but also has cloud execution modes (Replicate, fal.ai, Together AI). This creates semantic confusion.

**Secondary Issue:** `ProviderConfigScreen.kt` duplicates this logic differently:
```kotlin
private fun ProviderId.isCloudProvider(): Boolean {
    return when (this) {  // Note: OPPOSITE logic
        ProviderId.LOCAL_IMAGE,
        ProviderId.LOCAL_TEXT,
        ProviderId.LIQUID -> false
        else -> true  // FLUX treated as cloud!
    }
}
```

**DISCREPANCY:** ProviderId.isLocal() includes FLUX, but ProviderConfigScreen.isCloudProvider() treats FLUX as cloud!

---

## 5. MODEL CATALOG MISMATCH

### Issue 5.1: ProviderModelCatalog Missing Providers (CRITICAL)
**File:** `app/src/main/java/.../providers/ProviderModelCatalog.kt`

Present entries:
- OPENAI ✅
- GEMINI ✅
- ANTHROPIC ✅
- OPENROUTER ✅
- MISTRAL ✅
- DEEPSEEK ✅
- GROQ ✅
- XAI ✅
- COHERE ✅
- SILICON_FLOW ✅
- NOVITA ✅
- PIXAI ✅
- NOVELAI ✅
- OLLAMA_CLOUD ✅

**Missing entries (defined in ProviderId but NOT in catalog):**
- LOCAL_TEXT - Intentionally dynamic (documented in code)
- LOCAL_IMAGE - Intentionally dynamic (documented in code)
- LIQUID - Intentionally dynamic (documented in code)
- FLUX - Missing (should have entries)
- REPLICATE - Missing (should have entries)
- HUGGING_FACE - Missing
- ATLASCLOUD - Missing
- SIRAY - Missing
- AMAZON_BEDROCK - Missing

**Recommendation:** Add catalog entries for missing providers or mark as intentionally dynamic with comments.

---

## 6. DI CONFIGURATION CONSISTENCY

### Issue 6.1: Mixed DI Patterns (MODERATE)
**Issue:** ProviderAdapterFactory manually instantiates adapters instead of using DI:

```kotlin
// HotSwappingModule.kt provides:
@Provides
@Singleton
fun provideProviderAdapterFactory(okHttpClient: OkHttpClient, gson: Gson): ProviderAdapterFactory {
    return ProviderAdapterFactory(okHttpClient, gson)  // Factory then creates adapters
}
```

**Contrast:** Most other components use Hilt properly.

**Problem:** ProviderAdapters cannot be mocked in tests because they're created via factory, not injected.

### Issue 6.2: Legacy Provider Still Exists (MODERATE)
**File:** `app/src/main/java/.../providers/LiquidProvider.kt`

AGENTS.md states:
> "Existing `LiquidProvider` → Migrate to `LocalLlamaAdapter`"

**Status:** Migration INCOMPLETE. LiquidProvider.kt still exists in app module alongside the new adapter.

**Evidence:**
```kotlin
// AiServicesModule.kt:25
@Provides
@Singleton
fun provideTaskExecutionService(
    localLlmExecutor: LocalLlmExecutor,
    cloudLlmExecutor: CloudLlmExecutor,
    liquidProvider: com.shadowai.app.providers.LiquidProvider  // STILL HERE!
)
```

**Recommendation:** Complete migration or document why dual implementation exists.

---

## 7. SETTINGS UI COVERAGE

### Issue 7.1: Generic ProviderConfigScreen Used for All (MINOR)
**Observation:** All providers use the same `ProviderConfigScreen.kt` composable.

**What's working:**
- Cloud providers get API key field (correct)
- Local providers get host/port fields (correct)
- Model selection is generic but functional

**What's missing:**
- Provider-specific help text (e.g., where to get Ollama Cloud API key)
- Provider-specific validation (e.g., OpenAI key format sk-...)
- PixAI-specific model selection UI (it uses special model IDs)

**File:** `ProviderConfigScreen.kt` handles the generic case well, but line 115 hardcodes mapping for ALL providers:
```kotlin
private val ProviderId.displayName: String
    get() = when (this) {
        ProviderId.OPENAI -> "OpenAI"
        ProviderId.ANTHROPIC -> "Anthropic"
        // ... all 23 providers listed here
        ProviderId.UNKNOWN -> "Unknown Provider"
    }
```

**This is maintenance-heavy.** Should read from ProviderId.getDisplayName() instead.

---

## 8. CROSS-MODULE CONSISTENCY CHECKS

### ✅ core-contracts: PASSES
Only contains:
- Capability.kt (enum)
- Modality.kt (sealed class)
- ModelDescriptor.kt (data class)
- ProviderExecutor.kt (interface)
- ProviderId.kt (enum)
- Transform.kt (sealed class)

No implementation code. ✅ Clean.

### ✅ provider-adapters: MOSTLY PASSES
All implement `ProviderAdapter` interface. Structure is consistent.

Exception: LocalLlamaAdapter uses reflection (see Issue 3.2).

### ⚠️ app module: MIXED
**UI Layer:** Clean separation in `ui/` package.

**Data Layer:** Proper separation with repositories.

**Concern:** Direct dependency on `LocalLlamaAdapter` through reflection. Breaks clean architecture boundary.

### ✅ backend: PASSES
Properly isolated as separate Gradle project. No dependencies on app module. Uses Ktor, not Android.

---

## 9. CIRCULAR DEPENDENCY ANALYSIS

**Dependency Graph:**
```
app
├── core-contracts ✅
├── model-catalog ✅ (only core-contracts)
├── provider-adapters ✅ (core-contracts, model-catalog)
├── artifact-system ✅ (only core-contracts)
├── pipeline-planner ✅ (core-contracts, provider-adapters, artifact-system)
├── diagnostics ⚠️ (core-contracts, pipeline-planner)
├── hot-swapping ⚠️ (core-contracts, provider-adapters)
├── ui-params ✅ (only core-contracts)
└── ui-composition ⚠️ (core-contracts, ui-params, pipeline-planner, artifact-system)
```

**No circular dependencies detected.** All arrows point toward the center (core-contracts).

**Minor concern:** ui-composition depends on pipeline-planner which depends on provider-adapters. This creates a longer chain than ideal for UI modules.

---

## 10. ADDITIONAL FINDINGS

### Issue 10.1: Test Coverage Incomplete (MINOR)
Test files exist:
- `provider-adapters/src/test/.../LocalLlamaAdapterTest.kt` ✅
- `provider-adapters/src/test/.../ProviderAdapterFactoryTest.kt` ✅

**Missing tests for:**
- OllamaCloudAdapter
- OpenAICompatibleAdapter
- FluxAdapter
- ReplicateAdapter
- AnthropicAdapter
- GeminiAdapter
- PixAIAdapter
- NovitaAdapter
- NovelAIAdapter

### Issue 10.2: Unused Imports in Several Files (MINOR)
File: `ProviderConfigScreen.kt` - Has Android Import that may be unused (needs verification)

---

## RECOMMENDATIONS SUMMARY (PRIORITIZED)

### Critical (Fix Immediately)
1. **Fix ProviderId.isLocal() vs ProviderConfigScreen.isCloudProvider() discrepancy** - FLUX cannot be both local and cloud
2. **Complete LiquidProvider migration or remove** - Dual implementations cause confusion
3. **Add missing adapters** for HUGGING_FACE and AMAZON_BEDROCK
4. **Remove reflection from LocalLlamaAdapter** - Properly expose interface in core-contracts

### High (Fix This Sprint)
5. **Update AGENTS.md** with correct module count (12 not 8)
6. **Fix hot-swapping build.gradle** - Change `api` to `implementation`
7. **Add missing ProviderModelCatalog entries** for FLUX, REPLICATE, etc.
8. **Add ProviderAdapter initialization tracking** to all adapters

### Medium (Fix Next Sprint)
9. **Add missing unit tests** for all adapters
10. **Refactor ProviderConfigScreen.displayName** to use ProviderId.getDisplayName()
11. **Document intentional omissions** in ProviderModelCatalog comments

### Low (Backlog)
12. **Evaluate ui-composition dependencies** - Consider removing pipeline-planner dependency
13. **Add provider-specific validation** in config screen
14. **Standardize timeout values** across adapters (currently varies: 60s, 120s, 300s)

---

## CONCLUSION

The ShadowAi architecture has solid foundational structure with clean module boundaries and proper dependency direction. However, there are **critical inconsistencies** in:

1. Provider execution semantics (local vs cloud)
2. Incomplete migration of legacy components
3. Missing adapter implementations for declared ProviderId values
4. Reflection-based coupling that bypasses module boundaries

**Overall Rating: B- (Good structure, implementation inconsistencies)**

The issues are fixable within 1-2 sprints and don't require architectural overhaul.

---
*End of Audit Report*
