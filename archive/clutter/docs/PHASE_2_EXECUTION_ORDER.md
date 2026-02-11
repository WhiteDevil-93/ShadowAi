# PHASE 2 EXECUTION ORDER — CAPABILITY SCAFFOLD (NO BEHAVIOR)
# (AUTHORITATIVE · ACTIONABLE · SCOPE-LOCKED)

This document authorizes and instructs the execution of **Phase 2**.
All Phase-0 governance and Phase-1 platform scaffold are assumed complete and green.
No new contracts may be introduced during this phase.

────────────────────────────────────────
01 — PHASE 2 OBJECTIVE
────────────────────────────────────────

Phase 2 exists to define **where power will live** without turning it on.

Phase 2 answers one question only:

“Can the codebase express future capability (AI, routing, admin, device control)
as inert scaffolding without executing anything?”

No behavior. No side effects. No execution.

────────────────────────────────────────
02 — EXACT DELIVERABLES (WHITELIST)
────────────────────────────────────────

Jules is authorized to add **only inert scaffolding** consisting of:

Modules / packages (names indicative, not prescriptive):
- `core/` (pure contracts)
- `routing/` (policies, enums, reasons)
- `tasks/` (task models, states)
- `admin/` (interfaces only)
- `models/` (descriptors only)

Allowed artifacts:
- Interfaces
- Data classes
- Enums
- Sealed classes
- Type aliases
- Documentation comments

No concrete implementations.

────────────────────────────────────────
03 — REQUIRED SCAFFOLDS (INERT ONLY)
────────────────────────────────────────

You MUST define (without implementation):

A) Execution Source
- Enum: LOCAL | CLOUD
- No logic

B) Routing Policy
- FORCE_LOCAL
- FORCE_CLOUD
- AUTO
- Data-only explanation fields

C) Task Model
- TaskId
- TaskType
- State machine:
  QUEUED → RUNNING → COMPLETED | FAILED
- Optional JSON schema reference
- No executors

D) Model Descriptor
- ModelId
- Local/remote flag
- Capability tags
- No loaders

E) Admin Surface (interfaces only)
- Enable/disable models
- Override routing policy
- Inspect history
- No persistence

────────────────────────────────────────
04 — EXPLICITLY FORBIDDEN (HARD STOP)
────────────────────────────────────────

The following are NOT allowed in Phase 2:

Execution:
- Model loading
- Inference
- Network calls
- Background work
- Device interaction
- Scheduling

Architecture:
- Concrete classes with side effects
- Dependency injection frameworks
- Services / workers / receivers
- Threading, coroutines, flows

Dependencies:
- Any new runtime dependency
- Any library that implies execution
- Any SDK abstraction that hides control

If any execution occurs, Phase 2 is violated.

────────────────────────────────────────
05 — CI REQUIREMENTS (NON-NEGOTIABLE)
────────────────────────────────────────

During and after Phase 2:

- `01_toolchain_gate.yml` must pass
- `02_dependency_gate.yml` must pass (no new deps)
- `03_build_gate.yml` must pass

No test expansion.
No CI modification unless required to keep gates green.

────────────────────────────────────────
06 — IMPLEMENTATION DISCIPLINE
────────────────────────────────────────

Rules for Jules:
- Prefer pure Kotlin types
- No constructors with logic
- No default behaviors
- No TODOs that imply execution
- Comments allowed only to clarify intent

If a future behavior is tempting to add:
- Stop
- Leave it unimplemented
- Document the boundary in comments

────────────────────────────────────────
07 — PHASE 2 EXIT CONDITIONS
────────────────────────────────────────

Phase 2 is complete when:
- All scaffolds compile
- No side effects exist
- CI remains green
- Code expresses capability without action

Only after explicit human authorization may Phase 3 begin.

────────────────────────────────────────
08 — AUTHORITY STATEMENT
────────────────────────────────────────

This document authorizes Phase 2 execution.

No scope expansion is permitted.
No behavior is permitted.
No phase advancement is allowed without instruction.

End of Phase 2 execution order.
