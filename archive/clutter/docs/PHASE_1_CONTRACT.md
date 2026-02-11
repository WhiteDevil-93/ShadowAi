# Phase-1 Contract: Platform-Capable Shell

## Goal
Create a shell that can grow into the full assistant without refactors. This is NOT a simple demo.

## Required Properties
- **Architecture**: Must support future extensions for Accessibility, Foreground Services, and Background execution without architectural changes.
- **Manifest**: Permission structure must be expandable.
- **Components**: Navigation and ViewModel scaffolding is allowed and expected.
- **Feature Flags**: Allowed (must be inactive by default).

## Allowed Artifacts
- **Source Code**:
    - `MainActivity.kt` (Entry point)
    - ViewModels (Scaffolding only)
    - Navigation Graph (Scaffolding only)
- **Resources**:
    - Layouts for shell UI
    - Themes/Styles
- **Manifest**:
    - Placeholder permissions (commented out or strictly scoped)
    - Service declarations (if strictly required for architecture proof)

## Explicitly Forbidden
- **AI Inference**: No local or cloud model execution.
- **Network Calls**: No API requests.
- **Model Loading**: No downloading or initialization of models.
- **Device Control Execution**: No actual telephony, SMS, or accessibility actions.

## Gating Criteria
1.  **Contract Alignment**: Must pass `Contract Alignment Gate`.
2.  **Toolchain**: Must pass `01_toolchain_gate.yml`.
3.  **Build**: Must pass `03_build_gate.yml`.
