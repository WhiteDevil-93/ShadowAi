# LocalLlamaAdapter Fix Summary

## Problem
The `LocalLlamaAdapter.execute()` method at line ~94 was accepting an `inferenceManager` parameter but never actually using it for generation. It only:
1. Checked if `inferenceManager == null` and returned failure
2. Then immediately returned another failure without using the manager

## Solution
Fixed the `execute()` method to actually use the `inferenceManager` for local LLM generation via reflection (duck typing), avoiding module dependency issues.

## Changes Made

### File: `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/LocalLlamaAdapter.kt`

**OLD CODE (lines ~95-106):**
```kotlin
Log.d(TAG, "Executing TextToText: prompt=${prompt.take(50)}..., maxTokens=$maxTokens, temp=$temperature")
if (inferenceManager == null) {
    return@withContext Result.failure(
        UnsupportedOperationException(
            "Local inference backend is not wired for LocalLlamaAdapter."
        )
    )
}
Result.failure(
    UnsupportedOperationException(
        "Inference execution path is pending integration with LocalInferenceManager."
    )
)
```

**NEW CODE:**
```kotlin
Log.d(TAG, "Executing TextToText: prompt=${prompt.take(50)}..., maxTokens=$maxTokens, temp=$temperature")
if (inferenceManager == null) {
    return@withContext Result.failure(
        UnsupportedOperationException(
            "Local inference backend is not wired for LocalLlamaAdapter."
        )
    )
}

// Use reflection to call LocalInferenceManager methods
try {
    // Call inferenceManager.loadModel(config.baseUrl)
    val loadModelMethod = inferenceManager::class.java.getMethod("loadModel", String::class.java)
    val model = loadModelMethod.invoke(inferenceManager, config.baseUrl)
        ?: return@withContext Result.failure(
            IllegalStateException("Failed to load model: ${config.baseUrl}")
        )

    // Create GenerationConfig via reflection
    val configClass = Class.forName("com.shadowai.app.ai.LlamaNative\$GenerationConfig")
    val generationConfig = configClass.getDeclaredConstructor().newInstance()

    // Set maxTokens
    configClass.getDeclaredField("maxTokens").apply {
        isAccessible = true
        set(generationConfig, maxTokens)
    }

    // Set temperature
    configClass.getDeclaredField("temperature").apply {
        isAccessible = true
        set(generationConfig, temperature.toFloat())
    }

    // Call model.generateAsync(prompt, generationConfig)
    val generateAsyncMethod = model::class.java.getMethod(
        "generateAsync",
        String::class.java,
        configClass
    )

    @Suppress("UNCHECKED_CAST")
    val result = generateAsyncMethod.invoke(model, prompt, generationConfig) as Result<String>

    // Unload model after generation
    val unloadModelMethod = inferenceManager::class.java.getMethod(
        "unloadModel",
        Class.forName("com.shadowai.app.ai.LocalModel")
    )
    unloadModelMethod.invoke(inferenceManager, model)

    result
} catch (e: Exception) {
    // Cleanup on error - try to unload if we got the model
    runCatching {
        val loadModelMethod = inferenceManager::class.java.getMethod("loadModel", String::class.java)
        val model = loadModelMethod.invoke(inferenceManager, config.baseUrl)
        if (model != null) {
            val unloadModelMethod = inferenceManager::class.java.getMethod(
                "unloadModel",
                Class.forName("com.shadowai.app.ai.LocalModel")
            )
            unloadModelMethod.invoke(inferenceManager, model)
        }
    }
    Log.e(TAG, "Generation failed", e)
    Result.failure(e)
}
```

## Architecture Notes

- Used reflection to avoid adding a module dependency from `provider-adapters` to `app` module
- The fix follows the same pattern as other adapters (ReplicateAdapter, OpenAICompatibleAdapter) which:
  1. Load/initialize resources
  2. Execute the operation
  3. Clean up resources
- Proper error handling with cleanup in catch blocks

## Testing Required

```bash
./gradlew :provider-adapters:compileDebugKotlin
./gradlew :provider-adapters:testDebugUnitTest
```

## Integration Note

The `ProviderAdapterFactory` needs to be updated to pass the `LocalInferenceManager` instance when creating `LocalLlamaAdapter`:

```kotlin
// In ProviderAdapterFactory.kt
ProviderId.LOCAL_TEXT,
ProviderId.LOCAL_IMAGE,
ProviderId.LIQUID -> LocalLlamaAdapter(config, localInferenceManager)  // Pass manager
```

The factory currently creates the adapter without the manager, which means `inferenceManager` will always be null until the factory is updated.
