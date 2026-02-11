# Compose Compliance Checklist (UI/UX Orchestrator)

This checklist is designed to ensure all Jetpack Compose components within the UI/UX Orchestrator adhere to the specified design and technical requirements, including Material 3 compliance and Android system integration best practices.

## 1. Visual Hierarchy Requirements

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Conversation Dominance** | The primary conversation thread (user input/AI response) occupies the most prominent visual space and is not obscured by system elements. | [ ] | |
| **System State Subordination** | Status indicators, background tasks, and non-critical system information are visually subdued (e.g., lower opacity, smaller text, secondary color roles) relative to the conversation. | [ ] | |
| **Action Affordance Clarity** | Primary user actions (e.g., Send, Execute) are clearly distinguishable and use the Material 3 primary color role. | [ ] | |

## 2. Execution Context Handling

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Single Chip Presentation** | The execution context is represented by a single, non-dismissible chip or pill when active. | [ ] | |
| **Tap Behavior (Details)** | A single tap on the execution chip opens a non-modal bottom sheet or dialog with execution details and logs. | [ ] | |
| **Long-Press Behavior (Cancel/Stop)** | A long press on the execution chip presents a context menu with "Cancel" or "Stop Execution" options. | [ ] | |

## 3. Error Presentation Rules

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Inline Pill Usage** | Non-critical, transient errors are presented as inline, dismissible pills using the Material 3 error color role. | [ ] | |
| **Critical Error Dialog** | Critical, blocking errors (e.g., network failure, authentication failure) trigger a modal dialog that requires user acknowledgment. | [ ] | |
| **Retry Affordance** | All recoverable errors (inline or dialog) include a clearly labeled "Retry" or "Fix" action button. | [ ] | |

## 4. Keyboard (IME) Awareness

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **IME Padding Application** | The primary input field and its surrounding container correctly apply `imePadding()` to shift content up when the soft keyboard is visible. | [ ] | |
| **No Overlap** | The soft keyboard does not overlap or obscure the primary input field or any critical action buttons (e.g., Send button). | [ ] | |

## 5. System Insets Awareness

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Status Bar Padding** | Content at the top of the screen (e.g., `TopAppBar`) correctly uses `statusBarsPadding()` to avoid drawing under the system status bar. | [ ] | |
| **Navigation Bar Padding** | Content at the bottom of the screen (e.g., primary action bar) correctly uses `navigationBarsPadding()` to avoid drawing under the system navigation bar (if present). | [ ] | |
| **Full System Bars Padding** | The main content area uses `systemBarsPadding()` or a combination of the above to ensure all content is visible and interactive. | [ ] | |

## 6. Basic vs Advanced Mode Separation

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Mode Toggle Visibility** | A clearly labeled and accessible toggle or switch is available to switch between Basic and Advanced modes. | [ ] | |
| **Basic Mode Scope** | Basic mode hides all complex configuration options, developer logs, and advanced settings, focusing only on core conversation and execution. | [ ] | |
| **Advanced Mode Scope** | Advanced mode exposes all configuration, logs, and debugging tools without compromising the core conversation flow. | [ ] | |

## 7. Material 3 Compliance

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Color Roles** | All components strictly adhere to the defined Material 3 color roles (e.g., `primary`, `onPrimary`, `surface`, `onSurface`, `error`). | [ ] | |
| **Elevation Usage** | Components that require visual separation (e.g., modal dialogs, bottom sheets) use Material 3 elevation tokens (e.g., `tonalElevation`, `shadowElevation`). | [ ] | |
| **Typography Scale** | All text elements use the defined Material 3 typography scale (e.g., `display`, `headline`, `title`, `body`, `label`). | [ ] | |
| **Motion/Transitions** | All screen transitions and component state changes use Material 3-compliant motion curves and durations. | [ ] | |

## 8. Component Requirements

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Scaffold Usage** | The main screen composable uses `Scaffold` to correctly manage the layout of the `TopAppBar`, `SnackbarHost`, and main content. | [ ] | |
| **TopAppBar Implementation** | The `TopAppBar` (or `CenterAlignedTopAppBar`) is used for primary screen titles and navigation, adhering to Material 3 specifications. | [ ] | |
| **SnackbarHost Integration** | A `SnackbarHost` is correctly integrated into the `Scaffold` to display transient, non-critical messages. | [ ] | |

## 9. Motion Rules and Failure Conditions

| Requirement | Pass/Fail Criteria | Status | Notes |
| :--- | :--- | :---: | :--- |
| **State Change Animation** | All state changes (e.g., loading to success, button enabled to disabled) are animated using Compose's `animate*AsState` or `AnimatedContent` with appropriate Material 3 curves. | [ ] | |
| **Failure Condition Motion** | Error states or failure conditions (e.g., failed execution) are accompanied by a distinct, non-jarring motion (e.g., a subtle shake or color pulse) to draw attention without being aggressive. | [ ] | |
| **Performance (60 FPS)** | All animations and transitions maintain a smooth frame rate (target 60 FPS) on target devices, with no jank or dropped frames. | [ ] | |
