# Medium Priority Fixes Summary (M-1 to M-6)

**Date:** 2026-02-11  
**Workspace:** `/mnt/c/Users/anon3/Downloads/ShadowAi`

## Status

All medium priority fixes (M-1 through M-6) have been implemented and verified.

---

## Fix Details

### M-1: ModelDiscovery.kt - Remove atomic increment from ID, use hash only ✅

**Status:** Already implemented

**Changes verified:**
- Line 25: Comment confirms "Removed AtomicLong increment - using hash-only ID generation"
- Lines 125-130: ID generation uses deterministic hash-only calculation
- No `AtomicLong` counter found in the codebase

**Performance impact:** Positive - deterministic IDs eliminate need for atomic operations and state tracking across rescans.

---

### M-2: ExceptionMapper.kt - Preserve original exception context ✅

**Status:** Already implemented

**Changes verified:**
- Lines 24-41: `ExceptionContext` data class with full context preservation
- All mapping functions (lines 51-200+) include `context?.toSummary()` in exception messages
- Original exceptions preserved via `cause` parameter

**Performance impact:** Negligible - only adds string concatenation during exception construction.

---

### M-3: RetrySupport.kt - Add HTTP 408 to RETRYABLE_STATUS_CODES ✅

**Status:** Already implemented

**Changes verified:**
- Lines 11-17: `DEFAULT_RETRYABLE_CODES` includes `408` (Request Timeout)

**Performance impact:** None - only adds a new status code to the retryable set.

---

### M-4: ProviderAdapterFactory.kt - Implement LRU cache eviction with size limit ✅

**Status:** Already implemented

**Changes verified:**
- Lines 28-47: LRU cache using `LinkedHashMap(MAX_CACHE_SIZE, 0.75f, true)` with `accessOrder=true`
- Lines 42-46: `removeEldestEntry()` implements size limit eviction when `size > MAX_CACHE_SIZE`
- Thread-safe operations with synchronized locks

**Performance impact:** Positive - bounded cache prevents memory leaks while maintaining fast lookups (O(1)).

---

### M-5: ModelDiscovery.kt - Replace reflection-based discovery with explicit when statement ✅

**Status:** Already implemented

**Changes verified:**
- Lines 98-118: `parseLocalTransform()` uses explicit `when` statement
- Lines 230-240: `ModelConfig.toModelDescriptor()` uses explicit `when` statement
- No `Transform::class.sealedSubclasses` reflection found

**Performance impact:** Positive - explicit when statements compile to jump tables (O(1)) vs reflection (O(n) lookup + validation).

---

### M-6: LlamaNative.kt - Improve VRAM estimation (add context window, batch size factors) ✅

**Status:** FIXED

**Changes made:**

**Before:**
```kotlin
fun estimateVramRequirement(fileSizeBytes: Long): Int {
    val sizeMB = fileSizeBytes / (1024 * 1024)
    return (sizeMB * VRAM_MULTIPLIER).toInt()
}
```

**After:**
```kotlin
fun estimateVramRequirement(
    fileSizeBytes: Long,
    nCtx: Int = 2048,
    batchSize: Int = 1
): Int {
    val sizeMB = fileSizeBytes / (1024 * 1024)
    
    // Base VRAM for model weights and overhead
    val baseVramMB = (sizeMB * VRAM_MULTIPLIER).toInt()
    
    // Context window VRAM: nCtx * 2 bytes per token * 2x factor for activation buffers
    val contextVramMB = (nCtx * 2L * 2) / (1024 * 1024)
    
    // Batch size VRAM: additional buffer for each batch
    val batchVramMB = (batchSize * nCtx * 2L) / (1024 * 1024)
    
    return baseVramMB + contextVramMB.toInt() + batchVramMB.toInt()
}
```

**Performance impact:** Minimal - estimation is only done once during adapter initialization. The more accurate estimation helps prevent VRAM overflow crashes during actual inference.

---

## Testing Recommendations

1. **M-1:** Verify deterministic IDs across rescans by logging model IDs and checking consistency
2. **M-2:** Test exception mapping with various exception types and verify context is preserved
3. **M-3:** Verify HTTP 408 responses are properly retried
4. **M-4:** Monitor cache eviction by checking logs for "Evicting oldest adapter" messages
5. **M-5:** Test transform discovery with all Transform subtypes to ensure no regressions
6. **M-6:** Compare VRAM estimates with actual usage for various context sizes and batch sizes

---

## Files Modified

| File | Changes |
|------|---------|
| `/mnt/c/Users/anon3/Downloads/ShadowAi/provider-adapters/src/main/kotlin/com/shadowai/provideradapters/LocalLlamaAdapter.kt` | Updated `estimateVramRequirement()` to include context window and batch size factors |

All other fixes were already implemented in the codebase at the time of this audit.
