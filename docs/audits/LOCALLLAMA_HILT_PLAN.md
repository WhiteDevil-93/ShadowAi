# LocalLlamaAdapter Hilt DI Binding Plan

**Date:** 2026-02-09  
**Module:** provider-adapters → app dependency injection  
**Objective:** Enable proper Hilt DI for LocalInferenceManager/LocalInferenceEngine in LocalLlamaAdapter

---

## Executive Summary

The `LocalLlamaAdapter` currently receives `LocalInferenceManager` as an `Any?` parameter via reflection to avoid module dependency issues. This plan outlines the Hilt module changes needed to provide clean, type-safe dependency injection while maintaining architectural boundaries between `provider-adapters` and `app` modules.

---

## Current State Analysis

### AppModule.kt (Current)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideLocalInferenceManager(
        @ApplicationContext context: Context
    ): LocalInferenceManager = LocalInferenceManager(context)

    /**
     * Provides the LocalInferenceEngine interface bound to LocalInferenceManager.
     * This allows cross-module dependency injection while maintaining architecture boundaries.
     */
    @Provides
    @Singleton
    fun provideLocalInferenceEngine(
        manager: LocalInferenceManager
    ): LocalInferenceEngine = manager
    
    // ... other providers
}
```

### LocalLlamaAdapter (Current)
```kotlin
class LocalLlamaAdapter(
    override val config: ProviderAdapterConfig,
    private val inferenceManager: Any? = null  // Using reflection - NOT type-safe
) : ProviderAdapter
```

### ProviderAdapterFactory (Current)
```kotlin
class ProviderAdapterFactory(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private fun createAdapter(config: ProviderAdapterConfig): ProviderAdapter {
        return when (config.providerId) {
            ProviderId.LOCAL_TEXT,
            ProviderId.LOCAL_IMAGE,
            ProviderId.LIQUID -> LocalLlamaAdapter(config)  // NO inferenceManager passed!
            // ... other adapters
        }
    }
}
```

### Module Dependency Graph
```
┌─────────────────────┐
│     app module      │◄── contains AppModule.kt, LocalInferenceManager
│                     │    (Hilt @Singleton providers)
└──────────┬──────────┘
           │
           │ depends on
           ▼
┌─────────────────────┐
│ provider-adapters   │◄── contains LocalLlamaAdapter, ProviderAdapterFactory
│                     │    (NO Hilt, NO access to LocalInferenceManager)
└──────────┬──────────┘
           │
           │ depends on
           ▼
┌─────────────────────┐
│   core-contracts    │◄── contains LocalInferenceEngine interface
│                     │    (shared contract between app and provider-adapters)
└─────────────────────┘
```

---

## Required Changes

### 1. Add Hilt Dependencies to provider-adapters Module

**File:** `provider-adapters/build.gradle.kts`

**Add Dependencies:**
```kotlin
dependencies {
    // Existing dependencies...
    implementation(project(":core-contracts"))
    
    // Hilt dependencies (minimal set for dependency injection)
    implementation(libs.hilt.android)  // Or: "com.google.dagger:hilt-android:2.54"
    kapt(libs.hilt.compiler)           // Or: "com.google.dagger:hilt-compiler:2.54"
    
    // Keep existing networking and coroutines dependencies
}
```

### 2. Apply Hilt Plugin to provider-adapters

**File:** `provider-adapters/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)  // Add Hilt plugin
}

android {
    namespace = "com.shadowai.provideradapters"
    // ... rest of config
}
```

### 3. Update LocalLlamaAdapter for Constructor Injection

**File:** `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/LocalLlamaAdapter.kt`

```kotlin
package com.shadowai.provideradapters

import com.shadowai.core.LocalInferenceEngine  // From core-contracts
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Adapter for local GGUF models using llama.cpp.
 * Now supports proper constructor injection via Hilt.
 */
class LocalLlamaAdapter @Inject constructor(  // Add @Inject
    override val config: ProviderAdapterConfig,
    private val inferenceEngine: LocalInferenceEngine? = null  // Type-safe interface from core-contracts
) : ProviderAdapter {

    // ... existing companion object...

    override suspend fun execute(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        
        // Validate inference engine is available
        if (inferenceEngine == null) {
            return@withContext Result.failure(
                UnsupportedOperationException(
                    "LocalInferenceEngine not available. Ensure Hilt DI is properly configured."
                )
            )
        }

        when (transform) {
            is Transform.TextToText -> executeTextToText(input, parameters)
            else -> Result.failure(
                UnsupportedOperationException("Transform ${transform::class.simpleName} not supported")
            )
        }
    }

    /**
     * Execute TextToText using LocalInferenceEngine interface (type-safe, no reflection).
     */
    private suspend fun executeTextToText(
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> = withContext(Dispatchers.IO) {
        val prompt = input as String
        val maxTokens = parameters["maxTokens"] as? Int ?: 512
        val temperature = parameters["temperature"] as? Double ?: 0.7

        Log.d(TAG, "Executing TextToText: prompt=${prompt.take(50)}..., maxTokens=$maxTokens, temp=$temperature")

        try {
            // Use type-safe LocalInferenceEngine interface
            val modelPath = config.baseUrl
            
            // Load model
            val model = inferenceEngine!!.loadModel(modelPath)
                ?: return@withContext Result.failure(
                    IllegalStateException("Failed to load model: $modelPath")
                )

            // Generate text
            val result = model.generateAsync(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature
            )

            // Unload model
            inferenceEngine.unloadModel(model)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Result.failure(e)
        }
    }

    // ... rest of existing methods (remove reflection-based methods)...
}
```

### 4. Update ProviderAdapterFactory for Injection

**File:** `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderAdapterFactory.kt`

```kotlin
package com.shadowai.provideradapters

import com.shadowai.core.LocalInferenceEngine  // From core-contracts
import com.shadowai.core.ProviderId
import com.google.gson.Gson
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating provider adapters at runtime.
 * Now supports Hilt dependency injection for LocalInferenceEngine.
 */
@Singleton  // Add @Singleton for Hilt scoping
class ProviderAdapterFactory @Inject constructor(  // Add @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val inferenceEngine: LocalInferenceEngine?  // Injected from AppModule
) {
    private val adapterCache = ConcurrentHashMap<ProviderId, ProviderAdapter>()

    /**
     * Creates or retrieves a cached adapter for the given provider.
     */
    fun getAdapter(config: ProviderAdapterConfig): ProviderAdapter {
        return adapterCache.getOrPut(config.providerId) {
            createAdapter(config)
        }
    }

    /**
     * Creates a new adapter for the given configuration.
     * Now passes LocalInferenceEngine to LocalLlamaAdapter.
     */
    private fun createAdapter(config: ProviderAdapterConfig): ProviderAdapter {
        return when (config.providerId) {
            ProviderId.LOCAL_TEXT,
            ProviderId.LOCAL_IMAGE,
            ProviderId.LIQUID -> LocalLlamaAdapter(config, inferenceEngine)  // Pass engine!

            ProviderId.OPENAI,
            ProviderId.OPENROUTER,
            // ... other cloud providers use httpClient + gson
            -> OpenAICompatibleAdapter(config, httpClient, gson)

            ProviderId.ANTHROPIC -> AnthropicAdapter(config, httpClient, gson)
            
            ProviderId.GEMINI -> GeminiAdapter(config, httpClient, gson)
            
            // ... other adapters...

            ProviderId.HUGGING_FACE,
            ProviderId.AMAZON_BEDROCK -> throw IllegalArgumentException(
                "Provider ${config.providerId} is not yet fully supported."
            )

            ProviderId.UNKNOWN -> throw IllegalArgumentException("Unknown provider: ${config.providerId}")
        }
    }

    // ... existing cache management methods...
}
```

### 5. Create ProviderAdaptersModule (Optional - Advanced)

**File:** `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/di/ProviderAdaptersModule.kt`

```kotlin
package com.shadowai.provideradapters.di

import com.shadowai.core.LocalInferenceEngine
import com.shadowai.provideradapters.*
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for provider-adapters module internal bindings.
 * This module is only needed if you want to provide additional 
 * provider-specific bindings. The main LocalInferenceEngine binding
 * is already provided by AppModule in the :app module.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderAdaptersModule {

    /**
     * Provides the ProviderAdapterFactory with all required dependencies.
     * Note: LocalInferenceEngine is already bound in AppModule, so Hilt
     * will automatically resolve it here.
     */
    @Provides
    @Singleton
    fun provideProviderAdapterFactory(
        httpClient: OkHttpClient,
        gson: Gson,
        inferenceEngine: LocalInferenceEngine?  // Resolved from AppModule
    ): ProviderAdapterFactory {
        return ProviderAdapterFactory(httpClient, gson, inferenceEngine)
    }

    /**
     * Alternative: Provide a no-op LocalInferenceEngine for test builds
     * or when local inference is not available.
     */
    @Provides
    @Singleton
    fun provideNoOpLocalInferenceEngine(): LocalInferenceEngine? {
        // Return null for cloud-only builds
        return null
    }
}
```

### 6. No Changes Required to AppModule

The existing `AppModule` already provides everything needed:

```kotlin
// Already present in AppModule.kt - NO CHANGES NEEDED

@Provides
@Singleton
fun provideLocalInferenceManager(
    @ApplicationContext context: Context
): LocalInferenceManager = LocalInferenceManager(context)

@Provides
@Singleton
fun provideLocalInferenceEngine(
    manager: LocalInferenceManager
): LocalInferenceEngine = manager  // This is the key binding!
```

---

## Scoping Decisions

| Component | Scope | Rationale |
|-----------|-------|-----------|
| `LocalInferenceManager` | `@Singleton` | Expensive to create, manages native resources |
| `LocalInferenceEngine` | `@Singleton` | Same scope as manager (interface delegation) |
| `ProviderAdapterFactory` | `@Singleton` | Caches adapter instances |
| `LocalLlamaAdapter` | No scope / `@Reusable` | Created per-provider configuration, lightweight |

**Rationale:**
- `LocalInferenceManager` manages native llama.cpp resources which are expensive to initialize
- Factory caching prevents repeated adapter creation for the same provider
- Adapters themselves are stateless (except config) and can be recreated cheaply

---

## Implementation Order

1. **Phase 1:** Add Hilt dependencies to `provider-adapters/build.gradle.kts`
2. **Phase 2:** Update `LocalLlamaAdapter` constructor to use `LocalInferenceEngine`
3. **Phase 3:** Update `ProviderAdapterFactory` to accept `LocalInferenceEngine`
4. **Phase 4:** (Optional) Create `ProviderAdaptersModule` for explicit bindings
5. **Phase 5:** Update any direct instantiations of `ProviderAdapterFactory` to use Hilt injection

---

## Migration Considerations

### Breaking Changes
- `LocalLlamaAdapter` constructor signature changes from `(config, Any?)` to `(config, LocalInferenceEngine?)`
- `ProviderAdapterFactory` constructor now requires `LocalInferenceEngine?` parameter

### Backward Compatibility Options
```kotlin
// Option 1: Keep backward-compatible secondary constructor
class LocalLlamaAdapter @Inject constructor(
    override val config: ProviderAdapterConfig,
    private val inferenceEngine: LocalInferenceEngine?
) : ProviderAdapter {
    
    // Legacy constructor for manual instantiation
    constructor(config: ProviderAdapterConfig) : this(config, null)
}

// Option 2: Factory method with optional injection
@JvmOverloads
fun create(config: ProviderAdapterConfig, engine: LocalInferenceEngine? = null) = 
    LocalLlamaAdapter(config, engine)
```

### Files Requiring Updates Outside This Module
The following files likely instantiate `ProviderAdapterFactory` and will need Hilt injection:
- Any `ViewModel` creating adapters directly
- Service classes using the factory
- Test classes creating mock adapters

Example migration:
```kotlin
// BEFORE (manual creation):
class SomeViewModel : ViewModel() {
    private val factory = ProviderAdapterFactory(OkHttpClient(), Gson())
}

// AFTER (Hilt injection):
@HiltViewModel
class SomeViewModel @Inject constructor(
    private val factory: ProviderAdapterFactory
) : ViewModel()
```

---

## Verification Checklist

- [ ] `provider-adapters` module compiles with Hilt plugin
- [ ] `LocalLlamaAdapter` can be instantiated via constructor injection
- [ ] `ProviderAdapterFactory` receives `LocalInferenceEngine` from Dagger graph
- [ ] App builds successfully with `./gradlew :app:assembleDebug`
- [ ] Local inference works end-to-end (load model → generate → unload)
- [ ] Reflection code removed from `LocalLlamaAdapter`
- [ ] Unit tests updated to use mock `LocalInferenceEngine`

---

## Summary

This plan enables type-safe dependency injection for `LocalLlamaAdapter` without breaking module boundaries:

1. **Interface-Based DI:** Uses `LocalInferenceEngine` from `core-contracts` as the shared contract
2. **Minimal Changes:** Only 3 files require significant changes (gradle, adapter, factory)
3. **No AppModule Changes:** Existing Hilt bindings already provide what's needed
4. **Clean Architecture:** Maintains separation between `app` and `provider-adapters` modules
5. **Testable:** Interface-based injection enables easy mocking for unit tests

The key insight is that `LocalInferenceEngine` in `core-contracts` already provides the abstraction needed for cross-module DI. We just need to add Hilt to `provider-adapters` and wire the interface properly.
