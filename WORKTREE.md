# ShadowAi Worktree Guide

This file is the active execution guide for day-to-day implementation in this repo.

## Current Workflow Contract

1. Do implementation work first.
2. Do **not** run Gradle during in-progress edits.
3. Ask the user before any Gradle command (`compile`, `assemble`, `test`, `lint`).
4. Run builds/tests only after implementation is complete for the requested scope.

## Quick Start

- Preferred: `call scripts\dev-shell.cmd`
- Repo root: `cd /d c:\Users\anon3\Downloads\ShadowAi`
- Direct Git fallback: `scripts\git.cmd <args>`
- New feature worktree: `scripts\new-worktree.cmd <name> [base] [path]`

## Build Approval Gate

Before running any Gradle command, ask:

`Ready for validation build/test now?`

If approved, run the smallest useful command first:

1. `:app:compileDebugKotlin`
2. targeted module compile/test
3. `:app:assembleDebug` (only when needed)

## Validation Ladder

Use the minimum command that proves the change:

1. Type/DI safety: `./gradlew :app:compileDebugKotlin`
2. Module-specific behavior: `./gradlew :module:compileDebugKotlin` or targeted tests
3. Packaging only when requested: `./gradlew :app:assembleDebug`

Do not skip directly to full assemble unless needed for the requested outcome.

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

- This repo is initialized and supports Git worktrees.
- Current auxiliary worktree path: `c:\Users\anon3\Downloads\ShadowAi-worktree`
- If shell PATH is stripped, use `scripts\dev-shell.cmd` or `scripts\git.cmd`.
- For fast isolated work, create a named worktree from `master` using `scripts\new-worktree.cmd`.
- Prefer deterministic compile tasks and avoid repeated full builds unless requested.
