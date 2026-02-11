# Master Specification: Android AI Assistant
# (Post-Toolchain · Post-CI · No Duplication)

This document supplements existing governance, CI, and toolchain contracts.
It introduces no overlap with previously enforced constraints.

────────────────────────────────────────
## 01 — IRREVERSIBLE PRODUCT INTENT
────────────────────────────────────────

This application is a **full-surface Android AI assistant**, not a chatbot,
not a demo client, and not a thin UI shell.

Its defining property is **total future freedom**:
- Device-level integration
- Local-first intelligence
- Explicit cloud delegation
- Operator governance
- Transparent execution

No future change may reduce:
- Device reach
- Execution optionality
- Routing explicitness
- Admin authority
- Auditability

If a future decision limits any of the above, it is invalid by default.

────────────────────────────────────────
## 02 — LOCAL-FIRST IS A CAPABILITY, NOT A MODE
────────────────────────────────────────

Local execution is not a feature toggle.
It is a **first-class execution path**.

Requirements (not yet implemented):
- Local models must be discoverable at runtime
- Local execution must work offline
- Local execution must expose:
  - model identity
  - memory usage
  - readiness state
  - failure reasons
- Cloud execution must never silently replace local execution

Auto-routing is permitted only when:
- The decision is visible
- The decision is logged
- The decision is reversible
- The decision is user- or policy-driven

────────────────────────────────────────
## 03 — ROUTING IS POLICY, NOT HEURISTIC
────────────────────────────────────────

All execution routing must be governed by explicit policy.

Future routing states must include:
- FORCE_LOCAL
- FORCE_CLOUD
- AUTO (policy-based, explainable)

Every task execution must expose:
- Final routing decision
- Policy used
- Reason string
- Override source (user / admin / system)

No implicit fallbacks are allowed.
Failure is preferred over ambiguity.

────────────────────────────────────────
## 04 — ADMIN MODE IS MANDATORY
────────────────────────────────────────

The system must include a privileged control surface.

Admin capabilities (future, non-negotiable):
- Enable / disable models
- Adjust inference parameters
- Override routing policy
- Inspect execution history
- View audit logs
- Revert configuration changes

Admin actions must be:
- Authenticated
- Logged
- Reversible

There is no “hidden admin.”
If control exists, it must be visible.

────────────────────────────────────────
## 05 — TASKS ARE STRUCTURED ENTITIES
────────────────────────────────────────

Assistant actions are not messages — they are **tasks**.

Future task requirements:
- Unique task IDs
- Explicit task types
- Optional JSON schema
- State machine:
  queued → running → completed | failed
- Persisted history
- Machine-readable outputs where applicable

Free-text prompts are permitted,
but structured tasks are the default for actions.

────────────────────────────────────────
## 06 — UX TRANSPARENCY IS A CONTRACT
────────────────────────────────────────

The UI must never misrepresent execution.

Future UX constraints:
- Every response shows execution source (local / cloud)
- Model name is displayed when available
- Errors are explicit and non-blocking
- No background execution without confirmation
- No autonomous actions without user approval

If something happens, the user must see it.
If something fails, the user must know why.

────────────────────────────────────────
## 07 — PERMISSIONS ARE ARCHITECTURAL, NOT OPTIMIZED
────────────────────────────────────────

Permission breadth is intentional.

The project will eventually require:
- Telephony, messaging, contacts
- Location (including background)
- Media, camera, microphone
- Accessibility service
- Voice interaction service
- Connectivity control
- Notifications and alarms

Permissions are not minimized for optics.
They are justified by capability.

Optimization for distribution is a later phase concern.

────────────────────────────────────────
## 08 — EXPLICIT EXCLUSIONS
────────────────────────────────────────

The assistant must NEVER:
- Perform hidden monitoring
- Exfiltrate data silently
- Execute actions without confirmation
- Mask failures with fallbacks
- Make irreversible changes autonomously

If an action cannot be explained, it cannot occur.

────────────────────────────────────────
## 09 — IMPLEMENTATION DISCIPLINE
────────────────────────────────────────

At all times:
- Capability scaffolding precedes behavior
- Architecture precedes logic
- Transparency precedes polish

If implementation pressure conflicts with future freedom,
future freedom wins.

────────────────────────────────────────
## 10 — FINAL LOCK
────────────────────────────────────────

This assistant is not built to be approved.
It is built to be capable.

Constraints exist to preserve power, not limit it.
