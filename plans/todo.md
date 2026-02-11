# ShadowAi Project Roadmap - TODO List

This list outlines the complete roadmap for ShadowAi, merging architectural goals with critical fixes, following a Security-First, Stability-Next approach.

## Phase 1: Build Stability & DI Foundation (Days 1–5)
- [x] 1.1 Fix Build Blockers:
    - [x] Apply `@Parcelize` to `ModelDescriptor`.
    - [x] Add `kotlin-parcelize` plugin to `:model-catalog`.
    - [x] Reconcile package names (e.g., `PiiMaskingProcessor`) and move `TeeKeyManager` to `core-contracts` to fix import errors.
- [x] 1.2 Hilt & Module Boundaries:
    - [x] Implement `ModelCatalogModule.kt`.
    - [x] Implement `ProviderAdaptersModule.kt`.
    - [x] Address `SupervisorAgent` ↔ `ShadowAgent` circular dependency using `Lazy<T>` or `Provider<T>`.
    - [x] Consolidate `ProviderRepository` into a single interface in `core-contracts` with its implementation in `:provider-adapters`.

## Phase 2: The Security Shield (Days 6–10)
- [x] 2.1 Prompt Injection Defense:
    - [x] Implement `PromptInjectionDefense` using heuristic scoring and keyword density.
    - [x] Ensure screening happens before task identification.
- [x] 2.2 PII Masking (In/Out):
    - [x] Deploy `PiiMaskingProcessor` using `android.util.Patterns`.
    - [x] Implement mandatory masking for Cloud providers and optional for Local inference (configurable by user).
- [x] 2.3 Secure Key Handling:
    - [x] Fully integrate `SecretBytes` pattern.
    - [x] Replace all `String`-based API key storage with encrypted byte arrays that are zeroed out immediately after the HTTP request is built.

## Phase 3: IPC & Process Isolation (Days 11–17)
- [x] 3.1 AIDL Interface Definition:
    - [x] Implement `IInferenceService.aidl` in `:inference_process`.
    - [x] Implement `IGenerationCallback.aidl` in `:inference_process`.
    - [x] Use `ParcelFileDescriptor` for passing model files between processes.
- [x] 3.2 Isolated Service Setup:
    - [x] Create a `RemoteService` in the `:inference_process` module marked with `android:process=":inference"`.
    - [x] Implement a `DeathRecipient` in the main process to automatically restart the inference service if it hits an OOM and fails.

## Phase 4: Native Bridge & Capability Mapping (Days 18–25)
- [ ] 4.1 JNI Implementation:
    - [ ] Build `NativeBridge.kt`.
    - [ ] Build `InferenceEngine.cpp`.
    - [ ] Configure CMake to target `arm64-v8a` with `-O3` optimizations.
- [ ] 4.2 Capability-Based Selection:
    - [ ] Enrich `ModelDescriptor` with `Set<Capability>` (e.g., `VISION`, `REASONING`).
    - [ ] Update `TaskExecutor` to request models by `Capability` and latency.

## Phase 5: Pipeline Planner & Artifacts (Days 26–35)
- [ ] 5.1 Modality Transformation Graph:
    - [ ] Implement `PipelineGraph` (Modalities as nodes; Transforms as edges).
    - [ ] Use Dijkstra's algorithm to find the optimal path for user requests.
- [ ] 5.2 Unified Artifact System:
    - [ ] Refactor all adapters to return `Artifact` objects instead of raw `Strings` or `URIs`.
    - [ ] Ensure `Artifacts` include integrity hashes.

## Phase 6: Agentic Governance (Days 36–45)
- [ ] 6.1 The Planner/Critic Loop:
    - [ ] Implement `SupervisorAgent` to decompose tasks.
    - [ ] Implement `CriticAgent` to validate the output of the Planner.
    - [ ] Implement Adversarial Repair for `DEVICE_CONTROL` plans rejected by the Critic.
- [ ] 6.2 Context Management:
    - [ ] Implement a "Sliding Window" for conversation history.
    - [ ] Summarize the oldest turns into a single "Memory Artifact" when context hits 90% of model capacity.

## Phase 7: Performance & Final Polish (Days 46–50)
- [ ] 7.1 Memory Pressure Handling:
    - [ ] Implement `onTrimMemory` in the inference service to unload the Least Recently Used (LRU) model.
- [ ] 7.2 Adaptive UI Completion:
    - [ ] Finalize `AdaptiveChatLayout` for Foldables/Tablets.
- [ ] 7.3 Diagnostics UI:
    - [ ] Build the `ErrorReportScreen` to show "Inner Monologue" and pipeline logs for failed tasks.
