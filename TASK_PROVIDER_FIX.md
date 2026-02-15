# SHADOWAI Provider Adapter & Architecture Issues

## Task Details

**High Priority - Fix these 7 architecture issues:**

### 1. Remove ProviderRepository Facade
**File:** `ProviderRepository.kt`
**Issue:** Currently delegates to 5 split repositories, adds no logic
**Fix:**
- Remove the facade class
- Inject split repositories directly where needed
- Update all usages in AppModule, ViewModels, etc.

### 2. Fix Hilt DI Manual Provides
**Files:** `AppModule.kt`, `AiServicesModule.kt`
**Issue:** Manual `@Provides` for `@Inject` constructor classes
**Fix:**
- Remove manual provides for auto-resolvable classes
- Add missing bindings for new provider adapters
- Audit scoping (@Singleton vs per-Activity)

### 3. Complete Adapter Migration
**Files:** `TaskExecutor.kt`, `HybridAiExecutor.kt`
**Issue:** Two parallel paths (legacy direct calls + new adapter-based)
**Fix:**
- Remove legacy execution paths
- Complete migration to adapter-based execution only
- Ensure consistent error handling and retry logic

### 4. Implement Inference Switching
**File:** `SwitchableLocalInferenceEngine.kt`
**Issue:** `switchMode()` does nothing
**Fix:**
- Implement proper mode switching with cleanup
- Unload old engine, reload native libs
- Add fallback when isolated process crashes

### 5. Fix Race Condition
**File:** `ProviderSelector.kt:19-64`
**Issue:** Config list accessed outside mutex after index calculated inside
**Fix:** Access config list inside mutex lock

### 6. Fix Circular Dependency Risk
**File:** `ConversationSummarizer.kt:24`
**Issue:** Takes `LlamaNative` directly - potential circular deps
**Fix:** Create interface `ILlamaEngine`, inject interface

### 7. Handle Missing Features
- Ensure adapter cache invalidation on config change
- Add FUNCTION_CALLS capability mapping
- Fix streaming resource leaks
- Fix share intent handling in MainActivity
- Wire UI for features that exist but aren't integrated

## Acceptance Criteria
- No manual `@Provides` for `@Inject` classes
- No legacy execution paths remain
- Inference mode switching works correctly
- No race conditions in provider selection
- Hilt DI resolves all dependencies correctly

**Deliver immediately when complete.**