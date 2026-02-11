# Project Phase Order

## Phase-1 (Revised)
Platform-capable Android shell aligned to full assistant contract.
- Manifest and permission structure must NOT block future device integrations.
- Architecture must allow Accessibility, Foreground service, Background execution.
- Navigation + ViewModel scaffolding allowed.
- Feature flags allowed (inactive).
- Still Forbidden: AI inference, Network calls, Model loading, Device control execution.

## Phase-2
UI, navigation, permissions, admin mode (still no AI execution).

## Phase-3
Local + cloud inference, routing, governance, auditability.

## Phase-4
Device control execution (telephony, accessibility, automation).

No phase may reduce scope defined in ASSISTANT_CONTRACT_SCOPE.md.
