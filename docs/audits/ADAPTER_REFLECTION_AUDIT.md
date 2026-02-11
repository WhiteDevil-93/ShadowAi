# Provider Adapter Reflection Audit Report

**Date:** 2026-02-09  
**Auditor:** Subagent (Reflection Analysis)  
**Scope:** All provider adapters in `provider-adapters` module  
**Purpose:** Identify reflection usage, direct instantiation patterns, and module boundary violations

---

## Executive Summary

**CRITICAL ISSUE FOUND:** `LocalLlamaAdapter.kt` contains severe reflection-based violations that break module encapsulation. The adapter uses Java reflection to access internal classes from the `app` module (`com.shadowai.app.ai.*`), which violates clean architecture principles and creates brittle, unmaintainable code.

All other 10 adapters are **CLEAN** with proper dependency injection and no reflection issues.

---

## Adapters Audited

### 1. AnthropicAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java` for deserialization - acceptable)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 2. GeminiAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 3. OpenAICompatibleAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 4. NovitaAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 5. PixAIAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 6. NovelAIAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 7. FluxAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson` (nullable)
- **Boundaries:** No violations
- **Status:** No issues found

### 8. ReplicateAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 9. OllamaCloudAdapter.kt ✅ CLEAN
- **Reflection:** None (except Gson `::class.java`)
- **Injection:** Constructor-injected `OkHttpClient` and `Gson`
- **Boundaries:** No violations
- **Status:** No issues found

### 10. LocalLlamaAdapter.kt ❌ CRITICAL ISSUES
- **Reflection:** **SEVERE** - Uses runtime reflection to access app module internals
- **Injection:** Partial - accepts `Any?` for inferenceManager (avoids type safety)
- **Boundaries:** **VIOLATION** - Accesses `com.shadowai.app.ai.*` classes via reflection
- **Status:** **CRITICAL - Immediate refactoring required**

---

## Critical Issue: LocalLlamaAdapter Reflection Violations

### Location
`/provider-adapters/src/main/kotlin/com/shadowai/provideradapters/LocalLlamaAdapter.kt`

### Violations Found

#### 1. Direct Class Loading from App Module (Lines ~240-246)
```kotlin
// VIOLATION: Accessing app module class via reflection
val configClass = Class.forName("com.shadowai.app.ai.LlamaNative\$GenerationConfig")
val generationConfig = configClass.getDeclaredConstructor().newInstance()

// VIOLATION: Bypassing encapsulation with setAccessible
configClass.getDeclaredField("maxTokens").apply {
    isAccessible = true  // BREAKS ENCAPSULATION
    set(generationConfig, maxTokens)
}
configClass.getDeclaredField("temperature").apply {
    isAccessible = true  // BREAKS ENCAPSULATION
    set(generationConfig, temperature.toFloat())
}
```

#### 2. Method Reflection on App Module Classes (Lines ~200-210)
```kotlin
// VIOLATION: Accessing LocalModel class from app module
val unloadModelMethod = inferenceManager!!::class.java.getMethod(
    "unloadModel",
    Class.forName("com.shadowai.app.ai.LocalModel")  // EXTERNAL DEPENDENCY
)
```

#### 3. Dynamic Method Invocation (Lines ~220-230)
```kotlin
// VIOLATION: Method reflection instead of interface
val generateAsyncMethod = model::class.java.getMethod(
    "generateAsync",
    String::class.java,
    configClass
)
return generateAsyncMethod.invoke(model, prompt, generationConfig) as Result<String>
```

### Severity: 🔴 CRITICAL

| Aspect | Impact |
|--------|--------|
| **Maintainability** | High - String-based class names break on refactoring |
| **Type Safety** | High - Casting `Any?` and runtime method lookup |
| **Testability** | High - Cannot mock without reflection hacks |
| **Performance** | Medium - Reflection is slower than direct calls |
| **Module Boundaries** | Critical - Violates `provider-adapters` → `app` dependency rule |
| **Build Stability** | High - Will break if app module classes change |

### Why This Is Wrong

1. **Architecture Violation:** `provider-adapters` is a library module that should NOT know about `app` module internals
2. **Circular Dependency Risk:** App depends on provider-adapters, but this code depends on app classes
3. **Hidden Dependencies:** Compiler cannot verify these dependencies - they fail at runtime
4. **Refactoring Hazard:** Renaming classes in `app` module will silently break this adapter
5. **No Interface Abstraction:** Direct coupling to implementation classes instead of interfaces

---

## Minor Issue: ProviderAdapterFactory Direct Instantiation

### Location
`/provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderAdapterFactory.kt`

### Observation
The factory directly instantiates adapters via `when` statement:
```kotlin
ProviderId.ANTHROPIC -> AnthropicAdapter(config, httpClient, gson)
ProviderId.GEMINI -> GeminiAdapter(config, httpClient, gson)
// etc.
```

### Severity: 🟡 LOW

This is acceptable for a factory pattern, but could be improved with:
- Dagger/Hilt dependency injection
- ServiceLoader pattern for extensibility
- Registration-based factory for plugin architecture

### Recommendation
Consider migrating to Hilt DI bindings instead of manual factory, but this is **not urgent**.

---

## Clean Adapters Pattern (Reference Implementation)

All clean adapters follow this pattern:
```kotlin
class CleanAdapter(
    override val config: ProviderAdapterConfig,
    private val httpClient: OkHttpClient,  // Injected
    private val gson: Gson                  // Injected
) : ProviderAdapter {
    // No reflection
    // No external module dependencies
    // Pure Kotlin with proper types
}
```

---

## Recommended Fixes

### Immediate: LocalLlamaAdapter (Priority: CRITICAL)

#### Option A: Define Interface in Core Module (Recommended)
1. Create `LocalInferenceEngine` interface in `core-contracts` module
2. Have `app` module implement this interface
3. Inject the interface into `LocalLlamaAdapter`

```kotlin
// In core-contracts module
interface LocalInferenceEngine {
    suspend fun loadModel(path: String): LocalModelHandle
    suspend fun unloadModel(handle: LocalModelHandle)
    suspend fun generate(
        handle: LocalModelHandle,
        prompt: String,
        config: GenerationConfig
    ): Result<String>
}

data class GenerationConfig(
    val maxTokens: Int,
    val temperature: Float
)

// LocalLlamaAdapter becomes:
class LocalLlamaAdapter(
    override val config: ProviderAdapterConfig,
    private val inferenceEngine: LocalInferenceEngine  // Injected interface
) : ProviderAdapter {
    // No reflection needed - proper types!
}
```

#### Option B: Define Contract Interface in Provider-Adapters
1. Create `LocalInferenceManager` interface in `provider-adapters`
2. `app` module implements and injects it
3. Use typed interface instead of `Any?`

```kotlin
// In provider-adapters module
interface LocalInferenceManager {
    fun loadModel(modelPath: String): LocalModel
    fun unloadModel(model: LocalModel)
    fun generate(model: LocalModel, prompt: String, maxTokens: Int, temperature: Double): Result<String>
}

class LocalLlamaAdapter(
    override val config: ProviderAdapterConfig,
    private val inferenceManager: LocalInferenceManager  // Proper type
) : ProviderAdapter
```

### Optional: ProviderAdapterFactory Enhancement (Priority: LOW)
```kotlin
// Consider using Hilt multibindings
@Module
abstract class AdapterModule {
    @Binds
    @IntoMap
    @AdapterKey(ProviderId.ANTHROPIC)
    abstract fun bindAnthropicAdapter(adapter: AnthropicAdapter): ProviderAdapter
    // ... etc
}
```

---

## Testing Strategy for Fix

1. **Unit Test:** Verify `LocalLlamaAdapter` can be instantiated with mock `LocalInferenceEngine`
2. **Integration Test:** Verify adapter works with real implementation in `app` module
3. **Regression Test:** Ensure other adapters still work (no changes needed)
4. **Build Test:** Verify no reflection usages remain via static analysis

---

## Summary

| Adapter | Status | Severity | Action Required |
|---------|--------|----------|-----------------|
| AnthropicAdapter | ✅ Clean | - | None |
| GeminiAdapter | ✅ Clean | - | None |
| OpenAICompatibleAdapter | ✅ Clean | - | None |
| NovitaAdapter | ✅ Clean | - | None |
| PixAIAdapter | ✅ Clean | - | None |
| NovelAIAdapter | ✅ Clean | - | None |
| FluxAdapter | ✅ Clean | - | None |
| ReplicateAdapter | ✅ Clean | - | None |
| OllamaCloudAdapter | ✅ Clean | - | None |
| LocalLlamaAdapter | ❌ Issues | 🔴 Critical | Immediate refactor |
| ProviderAdapterFactory | 🟡 Observation | 🟡 Low | Optional improvement |

**Conclusion:** 10 of 11 adapters are clean. Only `LocalLlamaAdapter` requires immediate attention due to severe reflection-based module boundary violations.

---

## Appendix: Reflection Search Command

```bash
grep -r "Class.forName\|getDeclaredField\|getDeclaredMethod\|setAccessible" \
  provider-adapters/src/main/kotlin/
```

**Result:** Only `LocalLlamaAdapter.kt` contains these patterns (except Gson deserialization usages which are acceptable).

---

*Report Generated: 2026-02-09*  
*Auditor: Agent Pool - Subagent (Reflection Analysis)*
