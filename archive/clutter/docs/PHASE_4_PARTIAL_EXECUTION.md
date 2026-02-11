# PHASE 4 (PARTIAL) EXECUTION ORDER — DEVICE SURFACE SCAFFOLD
# (PERMISSIONS + STUBS ONLY · NO EXECUTION)

This document authorizes **Partial Phase 4**.
Phase 3 execution is complete and correct.
This phase prepares the Android device surface **without activating it**.

No new contracts may be introduced.
No device actions may occur.

────────────────────────────────────────
01 — PHASE OBJECTIVE
────────────────────────────────────────

Prepare the Android application to support future **device-level execution**
by declaring permissions and defining **non-executable stubs**.

Phase 4 (Partial) answers one question only:

“Is the application structurally capable of device integration
without performing any device action?”

────────────────────────────────────────
02 — PERMISSIONS (DECLARATION ONLY)
────────────────────────────────────────

You are authorized to **declare**, but NOT use, permissions in
`AndroidManifest.xml`.

Target permission surface (declare only if compatible with current SDK):

- INTERNET
- ACCESS_NETWORK_STATE
- BLUETOOTH / BLUETOOTH_CONNECT
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- READ_CONTACTS
- READ_PHONE_STATE
- SEND_SMS
- RECORD_AUDIO
- CAMERA
- POST_NOTIFICATIONS
- FOREGROUND_SERVICE
- BIND_ACCESSIBILITY_SERVICE (service not implemented)

Rules:
- No runtime permission requests
- No permission-dependent code paths
- No services activated
- No receivers registered
- No foreground/background execution

────────────────────────────────────────
03 — DEVICE INTERFACE STUBS (NO LOGIC)
────────────────────────────────────────

You may define **interfaces or abstract classes only** for:

- Telephony actions
- Messaging actions
- Accessibility actions
- Media control actions
- System interaction actions

Rules:
- No concrete implementations
- No Android API calls inside methods
- Methods may throw NotImplementedError or equivalent
- No default behavior

Purpose:
These stubs define *where* execution will later live,
not *how* it happens.

────────────────────────────────────────
04 — EXECUTION BOUNDARY (HARD STOP)
────────────────────────────────────────

The following are NOT allowed in this phase:

- Calling Android system APIs
- Starting services
- Registering receivers
- Requesting permissions at runtime
- Executing any device action
- Background or foreground execution
- Scheduling or automation

If any device action occurs, Phase 4 is violated.

────────────────────────────────────────
05 — CI REQUIREMENTS
────────────────────────────────────────

All existing CI gates must remain green:

- 01_toolchain_gate.yml
- 02_dependency_gate.yml
- 03_build_gate.yml

No new CI workflows are permitted unless required to keep gates green.

────────────────────────────────────────
06 — IMPLEMENTATION DISCIPLINE
────────────────────────────────────────

Rules for Jules:
- Prefer interfaces over classes
- Keep stubs minimal and explicit
- Do not add logging
- Do not add TODOs implying execution
- Do not anticipate Phase 5 logic

If tempted to “just wire it”:
- Stop
- Leave it inert

────────────────────────────────────────
07 — EXIT CONDITIONS
────────────────────────────────────────

Partial Phase 4 is complete when:
- Permissions are declared but unused
- Device capability stubs exist
- No device actions are possible
- CI remains green

Only after explicit human authorization may
**Full Phase 4 (device execution)** or **Phase 5** begin.

────────────────────────────────────────
08 — AUTHORITY STATEMENT
────────────────────────────────────────

This document authorizes Partial Phase 4 execution only.

No execution.
No autonomy.
No phase advancement without instruction.

End of Partial Phase 4 execution order.
