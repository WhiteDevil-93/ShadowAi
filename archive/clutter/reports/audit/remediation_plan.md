# Remediation Plan (Ordered)

## 1) Unblock Build + CI (P0)
1. Fix `:app:compileDebugKotlin` hard failures in AI/execution modules first:
   - `app/src/main/java/com/shadowai/app/ai/LocalBrainManager.kt`
   - `app/src/main/java/com/shadowai/app/ai/ModelDownloader.kt`
   - `app/src/main/java/com/shadowai/app/execution/CloudLlmExecutor.kt`
   - `app/src/main/java/com/shadowai/app/execution/DeviceAction.kt`
   - `app/src/main/java/com/shadowai/app/execution/LocalLlmExecutor.kt`
   - `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt`
2. Resolve `ui-validator` API compatibility with the current lint API:
   - remove/replace invalid `IssueRegistry.getReporters` override
   - migrate reporter implementation to supported APIs
   - update detector usages that rely on removed methods.
3. Stabilize flaky provider adapter circuit-breaker test:
   - `provider-adapters/src/test/kotlin/com/shadowai/provideradapters/AdapterCircuitBreakerTest.kt:57`.

## 2) Security + Privacy (P1)
1. Remove API key in Gemini query strings:
   - `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderAdapterFactory.kt:187`.
2. Continue replacing key-to-String conversions in runtime paths (where still present outside UI state).
3. Add tests to assert no key-bearing query params are constructed.

## 3) Accessibility + User-Facing Reachability
1. Keep no-op regressions blocked with explicit action registry checks.
2. Wire lint JSON generation into compliance task so `UI_COMPLIANCE_REPORT.md` is evidence-based.
3. Add Compose UI tests for:
   - drawer profile entry
   - provider "Get Key" link
   - chat mode tab actions.

## 4) Maintainability
1. Finish migration away from deprecated `kotlinOptions.jvmTarget` usage in remaining modules.
2. Reduce module build-time noise from generated/vendor history by completing index cleanup commit.
3. Add CI job that runs:
   - `cmd.exe /c gradlew.bat help projects :app:tasks --all`
   - `cmd.exe /c gradlew.bat :provider-adapters:test`
   - lint/checks once `ui-validator` compiles.
