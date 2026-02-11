# DarcyAI Android - Architecture V1

## Overview
DarcyAI Android is a local-first, agentic AI assistant for Android 16. It implements a layered architecture enabling autonomous device control, policy-based execution, and governance.

## Key Layers

### 1. Phase 5: Intelligence (Agent)
*   **DarcyAgent (`com.darcyai.android16.agent.DarcyAgent`):** The central "brain". Parses natural language inputs, creates `Task` objects, and delegates them to the Execution Engine.
*   **Routing:** Uses `SimpleRoutingEngine` to determine if a task should run locally or (simulated) cloud.

### 2. Phase 3: Execution Engine
*   **TaskExecutor (`DefaultTaskExecutor`):** Orchestrates task execution. It enforces governance policies (`AdminContract`) before running a task.
*   **Executors:**
    *   `LocalLlmExecutor`: Uses heuristics and regex to simulate a local LLM, handling device commands and simple chat.
    *   `CloudLlmExecutor`: Simulates a cloud-based model for general knowledge fallback.

### 3. Phase 4: Device Integration
*   **Implementation (`com.darcyai.android16.device.implementation`):** Concrete classes interfacing with Android APIs.
    *   `AndroidSystemInteraction`: WiFi, App Launching.
    *   `AndroidMediaControl`: Volume, Playback.
    *   `AndroidMessaging`: SMS Send/Read.
    *   `AndroidTelephony`: Call Start/End.
    *   `DarcyAccessibilityService`: Global Actions (Home, Back), Screen Inspection.

### 4. Phase 5: Governance (Admin)
*   **AdminRepository (`com.darcyai.android16.admin.implementation`):** Manages the global `RoutingPolicy` (persisted via `SharedPreferences`) and audit logs (`ExecutionHistory`).
*   **UI:** `AdminActivity` allows users to toggle policies (Auto/Local/Cloud) and view logs.

## Lifecycle & State
*   **DarcyApplication:** Initializes singletons (like `AdminGlobals`) on startup.
*   **MainActivity:** Provides the chat interface and initializes the Agent.

## Permissions
The app requests broad permissions (Call, SMS, Location, Accessibility) to function as a true assistant. It handles failures gracefully if permissions are denied.
