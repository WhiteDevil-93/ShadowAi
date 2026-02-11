# Runtime + UI Access Audit (Raw Findings)

Audit date: 2026-02-07

## Commands executed
- `cmd.exe /c gradlew.bat help` (pass)
- `cmd.exe /c gradlew.bat projects` (pass)
- `cmd.exe /c gradlew.bat :app:tasks --all` (pass)
- `cmd.exe /c gradlew.bat lintDebug testDebugUnitTest :provider-adapters:test --continue --stacktrace` (failed)
- `cmd.exe /c gradlew.bat :ui-validator:generateComplianceReport -Plint.results.dir=lint-results -Plint.baseline.dir=lint-baseline` (pass after creating output dirs)
- `cmd.exe /c gradlew.bat :app:kspDebugKotlin :provider-adapters:testReleaseUnitTest --continue -x :ui-validator:compileDebugKotlin --stacktrace` (pass)

## Baseline blockers addressed
- Fixed: Unix wrapper CRLF issue (`gradlew` normalized to LF; `.gitattributes` added).
- Fixed: backend plugin classpath/version conflict (`backend/build.gradle.kts:2` now uses `id("org.jetbrains.kotlin.jvm")`).
- Fixed: manifest network config wiring by adding fallback resource (`app/src/main/res/xml/network_security_config.xml`).
- Fixed: localhost cleartext contradiction by aligning network configs:
  - `app/src/release/res/xml/network_security_config.xml`
  - `app/src/main/res/xml/network_security_config_release.xml`
- Fixed: accessibility lint guardrails re-enabled (`app/lint.xml:22`).
- Fixed: UI validator accessibility issue registered (`ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/UiValidatorIssueRegistry.kt:41`).
- Fixed: generated/vendor index drift cleanup performed via `git update-index --force-remove` for `backend/node_modules/**` and `*/build/**`.
  - Verification: `git ls-files backend/node_modules | wc -l` => `0`; `git ls-files '*/build/*' | wc -l` => `0`.

## P0 / P1 unresolved findings

### P0 - `:app:compileDebugKotlin` is not green (multiple pre-existing compile errors)
- Repro: `cmd.exe /c gradlew.bat :app:testDebugUnitTest --tests "com.shadowai.app.ui.audit.UserFacingActionRegistryTest"`
- Representative failures:
  - `app/src/main/java/com/shadowai/app/ai/LocalBrainManager.kt:18` (multiple companion objects)
  - `app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt:337` (`Mutex.withLock` used outside suspend context)
  - `app/src/main/java/com/shadowai/app/ai/ModelDownloader.kt:104` (type mismatch + unresolved symbols later in file)
  - `app/src/main/java/com/shadowai/app/execution/DeviceAction.kt:3` (kotlinx.serialization.json symbols unresolved)
  - `app/src/main/java/com/shadowai/app/execution/HybridAiExecutor.kt:261` (`withContext` / `Dispatchers` unresolved imports)
  - `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt:68` (missing `LocalRuntimeConfig` symbol)
- Impact: app unit test pipeline cannot be considered stable yet.

### P0 - `ui-validator` module does not compile
- Repro: `cmd.exe /c gradlew.bat lintDebug testDebugUnitTest :provider-adapters:test --continue --stacktrace`
- Primary failures:
  - `ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/UiValidatorIssueRegistry.kt:47` (`getReporters` override mismatch)
  - `ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/UiLintReporter.kt:8` (Reporter/LintDriver API mismatch)
  - `ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/ExecutionContextDetector.kt:19` (`getParameterForArgument` unresolved)
  - Similar API mismatch errors across multiple detectors.
- Impact: Full lint pipeline cannot go green while `ui-validator` remains API-incompatible.

### P0 - provider adapter test instability observed
- Repro (first run): same full sweep command above.
- Failure:
  - `provider-adapters/src/test/kotlin/com/shadowai/provideradapters/AdapterCircuitBreakerTest.kt:57`
  - Test: `OPEN state throws CircuitOpenException`
- Note: focused rerun later passed; treat as flaky until stabilized.

### P1 - API key still sent via query parameter in adapter implementation
- File: `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderAdapterFactory.kt:187`
- Finding: Gemini adapter builds URL with `?key=${config.apiKey}`.
- Impact: Key exposure in logs/proxies/browser history equivalents.

### P1 - UI compliance reporting pipeline not connected to actual lint JSON
- File: `ui-validator/lint-results/UI_COMPLIANCE_REPORT.md:3`
- Finding: report generated with "No lint report found", indicating missing upstream lint JSON production in this run.
- Impact: compliance report can show false clean state.

## User-facing action accessibility/function results
- Fixed: profile drawer action no longer no-op (`app/src/main/java/com/shadowai/app/ui/chat/NavigationDrawerContent.kt:151`).
- Fixed: provider "Get Key" action now opens provider portal URL (`app/src/main/java/com/shadowai/app/ui/providers/ProviderSelectionScreen.kt:300`).
- Fixed: chat mode tabs no longer inert:
  - `WRITE` and `CALL` now trigger explicit behavior/snackbar and mode-aware prompt formatting (`app/src/main/java/com/shadowai/app/ui/chat/ChatScreen.kt:147`).
- Fixed: deep-link provider parsing hardened (`app/src/main/java/com/shadowai/app/ui/navigation/ShadowAINavGraph.kt:106`, `app/src/main/java/com/shadowai/app/providers/ProviderModels.kt:10`).
- Fixed: plaintext API keys removed from long-lived provider UI state:
  - `app/src/main/java/com/shadowai/app/ui/providers/ProviderSelectionViewModel.kt:20`
  - `app/src/main/java/com/shadowai/app/ui/providers/ProviderConfigViewModel.kt:18`

## Additional notes
- During sweep, missing imports caused app KSP failures; corrected in:
  - `app/src/main/java/com/shadowai/app/ui/settings/UsageCreditsViewModel.kt:3`
  - `app/src/main/java/com/shadowai/app/providers/ActiveProviderManager.kt:3`
  - `app/src/main/java/com/shadowai/app/ui/providers/ProviderSelectionViewModel.kt:5`
- Focused KSP + provider test rerun passed once `ui-validator` compile task was excluded.
