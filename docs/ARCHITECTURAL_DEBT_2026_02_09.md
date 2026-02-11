# Architectural Debt Review - 2026-02-09

**Context**: Code review feedback received. These are architectural improvements, NOT deployment blockers. All critical runtime issues have been resolved.

## Summary Assessment
The adapter implementations (particularly NovitaAdapter highlighted, but applies to pattern across adapters) function correctly but have structural issues that impact long-term maintainability.

---

## High-Level Architectural Issues

### 1. Monolithic Class Structure (Refactoring Recommended)
**Problem**: Data classes and sealed exceptions defined as inner classes within adapter.

```kotlin
// Current (anti-pattern)
class NovitaAdapter {
    data class NovitaTextToImageRequest(...)  // Inner class
    sealed class NovitaException(...)  // Inner class
}
```

**Recommendation**: Move to top-level files:
```
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/novita/
├── NovitaAdapter.kt
├── NovitaModels.kt          (all data classes)
└── NovitaExceptions.kt      (sealed exception hierarchy)
```

**Priority**: Low (not a blocker) | **Effort**: Medium

---

### 2. Confusing Dual Public API
**Problem**: Adapter implements `ProviderAdapter.interface` (execute/canExecute) AND provides parallel typed methods (generateImage/generateAudio/etc).

```kotlin
// Interface method
override suspend fun execute(transform, input, parameters): Result<Any>

// Parallel typed method
suspend fun generateImage(prompt, width, height): Result<Any>

// Boilerplate method
override suspend fun generateAudio(): Result<Any> = 
    Result.failure(UnsupportedOperationException())
```

**Issue**: Trying to serve two masters - generic interface AND typed convenience API.

**Recommendation**: Either:
- A) Remove typed methods, use only interface methods with proper parameter objects
- B) Make typed methods the primary API, interface methods delegate to them
- C) Create a typed adapter interface layer on top of generic base

**Priority**: Low (functions correctly) | **Effort**: High

---

### 3. Redundant Timeout Logic (Correctness Issue)
**Problem**: Two timeout sources that can conflict:

```kotlin
// Outer timeout
withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
    // Inner timeout loop
    pollForTaskCompletion()  // uses config.timeoutSeconds
}
```

**Bug Risk**: If `DEFAULT_TIMEOUT_MS` < `config.timeoutSeconds`, poll never completes naturally.

**Fix**: Single source of truth:
```kotlin
val effectiveTimeout = minOf(DEFAULT_TIMEOUT_MS, config.timeoutSeconds * 1000L)
withTimeoutOrNull(effectiveTimeout) { ... }
```

**Priority**: Medium (logical bug) | **Effort**: Low

---

## Code-Level Issues

### 4. Unsafe Parameter Handling
**Problem**: Unsafe casts from Map<String, Any>:
```kotlin
val width = parameters["width"] as? Int ?: 512  // Silent fallback
```

**Issues**:
- Wrong types silently fallback to defaults
- Missing parameters can indicate bugs (should not be silent)

**Recommendation**: Type-safe parameter classes:
```kotlin
data class TextToImageParams(
    val width: Int,
    val height: Int,
    val steps: Int
) {
    companion object {
        fun fromMap(parameters: Map<String, Any>): TextToImageParams {
            val width = (parameters["width"] as? Int)
                ?: throw IllegalArgumentException("Missing required 'width' parameter")
            // ... validate all required params
            return TextToImageParams(width, height, steps)
        }
    }
}
```

**Priority**: Medium (error-prone) | **Effort**: Medium

---

### 5. Inefficient Image Encoding
**Problems**:
1. Magic byte parsing for MIME type (fragile):
   ```kotlin
   when {
       imageBytes.size >= 2 && imageBytes[0] == 0xFF.toByte() && imageBytes[1] == 0xD8.toByte() -> "image/jpeg"
       // ...
   }
   ```
2. Constructs data URL then immediately strips it:
   ```kotlin
   val dataUrl = encodeImageToBase64(...)  // "data:image/jpeg;base64,/9j/..."
   val base64Image = dataUrl.substringAfter(",")  // Wasted work
   ```

**Fixes**:
1. Require caller to provide MIME type, or use a proper library
2. Separate concerns: raw Base64 encoder + data URL builder

**Priority**: Low (works but inefficient) | **Effort**: Low

---

### 6. Basic Polling Without Backoff
**Problem**: Naive polling loop:
```kotlin
while (...) {
    poll()
    delay(pollIntervalMs)  // Fixed interval
}
```

**Issues**:
- No exponential backoff
- Wastes resources on fast-polling tasks
- Could overwhelm API endpoints

**Recommendation**: Use existing `RetrySupport` pattern with exponential backoff.

**Priority**: Low (works but not optimized) | **Effort**: Medium

---

### 7. Limited Retry Mechanism
**Problem**: Only retries on IOException:
```kotlin
withRetryInternal {
    httpClient.newCall(request).execute()  // Only catches IO failures
}
```

**Missing**: Should retry on:
- HTTP 503 Service Unavailable
- HTTP 502 Bad Gateway
- HTTP 429 Rate Limit (with backoff)
- HTTP 504 Gateway Timeout

**Fix**: Make retry logic inspect response codes.

**Priority**: Medium (resilience issue) | **Effort**: Low

---

### 8. Missing Initialization Check
**Problem**: `initialize()` sets flag, but `execute()` never checks it:
```kotlin
override suspend fun initialize(): Boolean {
    isInitialized = validateConfig()  // Sets flag
}

override suspend fun execute(): Result<Any> {
    // Never checks isInitialized - proceeds to network call
    // Will fail with cryptic error instead of clear "not initialized" message
}
```

**Fix**: Add guard at start of execute:
```kotlin
if (!isInitialized) {
    return Result.failure(IllegalStateException("Adapter not initialized. Call initialize() first."))
}
```

**Priority**: Medium (error clarity) | **Effort**: Low

---

### 9. Superficial Error Parsing
**Problem**: 400 errors dump raw response:
```kotlin
is 400 -> Result.failure(Base64IllegalStateException("Bad Request: $errorBody"))
// errorBody might be JSON: {"error": {"message": "Invalid API key", "code": "auth_failed"}}
```

**Fix**: Parse structured error responses:
```kotlin
is 400 -> {
    val parsedError = parseJsonError(errorBody)
    Result.failure(NovitaException.ApiError(parsedError.message, parsedError.code))
}
```

**Priority**: Low (works but poor UX) | **Effort**: Medium

---

### 10. Other Nitpicks
- **Unchecked casts**: `@Suppress("UNCHECKED_CAST")` on `generateImage` - type erasure issue from `Result<Any>`
- **Redundant API key fetches**: Call `config.getApiKey()` multiple times in same path - fetch once
- **Hardcoded defaults**: `512`, `28`, `1` should be constants in companion object

---

## The Verdict

| Category | Status |
|----------|--------|
| **Functionality** | ✅ Working |
| **Runtime Stability** | ✅ No known crashes |
| **Code Structure** | ⚠️ Monolithic, needs decomposition |
| **Type Safety** | ⚠️ Map casting is brittle |
| **Error Handling** | ⚠️ Could be more robust |
| **Resource Efficiency** | ⚠️ Polling not optimized |

**Assessment**: Code "functions, but cuts corners on structure, safety, and robustness." (reviewer quote)

---

## Priority Matrix

### High Priority (Technical Debt)
- [ ] 3. Redundant timeout logic (actual bug)
- [ ] 8. Missing initialization checks

### Medium Priority
- [ ] 4. Type-safe parameter handling
- [ ] 7. Expanded retry logic (503, 502, 429)

### Low Priority (Cleanup)
- [ ] 1. Extract inner classes to files
- [ ] 2. Clean up dual API confusion
- [ ] 5. Efficient image encoding
- [ ] 6. Backoff for polling
- [ ] 9. Structured error parsing
- [ ] 10. Nitpicks (casts, constants)

---

## Depolement Status
**ALL CRITICAL BLOCKERS RESOLVED**. These are architectural improvements for future releases.

Recommended: Address high priority items in next sprint. Defer medium/low to refactoring phase.
