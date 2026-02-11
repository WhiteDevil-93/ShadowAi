# ShadowAi Worktree Guide

This file is the active execution guide for day-to-day implementation in this repo.

## Current Workflow Contract

1. Do implementation work first.
2. Do **not** run Gradle during in-progress edits.
3. Ask the user before any Gradle command (`compile`, `assemble`, `test`, `lint`).
4. Run builds/tests only after implementation is complete for the requested scope.

## Build Approval Gate

Before running any Gradle command, ask:

`Ready for validation build/test now?`

If approved, run the smallest useful command first:

1. `:app:compileDebugKotlin`
2. targeted module compile/test
3. `:app:assembleDebug` (only when needed)

## Active Worktree Scope

- `app/`: UI, orchestration, DI, agent loop, runtime behavior
- `core-contracts/`: shared contracts, security primitives, PII processing
- `provider-adapters/`: provider integrations and repository impl
- `model-catalog/`: discovery, descriptor metadata, deduplication
- `inference_process/`: isolated inference process, AIDL/JNI boundary
- `pipeline-planner/`: modality routing and transform planning

## Phase Focus

- Phase 1-3 cleanup and stability hardening
- Phase 3 isolation by configuration (switchable local engine path)
- Phase 4+ security/thread/perf follow-up only when requested

## Environment Notes

- `git` CLI is unavailable in current shell, so Git worktree/branch commands cannot be executed here.
- If Git becomes available, use proper `git worktree` flow for isolated feature branches.
- In this environment, prefer deterministic compile tasks and avoid repeated full builds unless requested.
