# MASTER ROADMAP — API-DRIVEN AGENTIC ANDROID SYSTEM

## SYSTEM AXIOMS (NON-NEGOTIABLE)

1. **The API is not trusted by default**
2. **No reasoning step is final without verification**
3. **The client must be able to halt the backend**
4. **Failure is a first-class signal**
5. **All autonomy is budgeted**

Everything below enforces those axioms.

---

## PHASE 0 — PROTOCOL HARDENING & OBSERVABILITY (FOUNDATION)

> Goal: Make the system *inspectable, replayable, and stoppable*

### 0.1 Versioned API Contracts (MANDATORY PATCH)

* Enforce **strict schema versioning** for:
  * Plans
  * Tool calls
  * Memory writes
* Reject:
  * Unknown fields
  * Schema drift
  * Silent defaults

**Failure Mode Addressed**

* Backend semantic drift
* “Looks valid but isn’t” responses

---

### 0.2 Client-Side Execution Ledger

**Upgrade**

* Android maintains an **append-only execution log**:

  ```
  step_id
  api_call_hash
  response_hash
  verification_status
  timestamp
  ```

* Ledger survives:
  * App kill
  * Network loss
  * Device reboot

**Why**

* Client must be able to **prove backend misbehavior**

---

### 0.3 Unified Error Taxonomy

**Patch**
All errors normalized into:

* TRANSPORT (timeouts, disconnects)
* SEMANTIC (wrong but valid)
* LOGIC (contradictions)
* VIOLATION (contract breach)
* EXHAUSTION (budget exceeded)

**Critical**

* LLM error ≠ system error

---

## PHASE I — RECURSIVE SELF-CORRECTION (API-CENTRIC)

> Goal: Backend fixes itself; client enforces discipline

---

### 1.1 Explicit Repair FSM (SERVER)

**Upgrade**
Backend runs a **finite state repair machine**:

```
FAIL →
  CLASSIFY →
    ROOT_CAUSE →
      PATCH →
        VERIFY →
          COMMIT | ROLLBACK
```

**Rules**

* Max N repair loops
* Every patch must generate a **diff**
* No hidden “reflection”

---

### 1.2 Client-Side Verification Gates

**Patch**
Android client verifies:

* Plan validity
* Step preconditions
* Postconditions
* Confidence thresholds

Client can:

* Reject a step
* Request re-plan
* Force abort

**This is critical:**
The backend does **not** get unilateral authority.

---

### 1.3 Self-Correction Budgeting

**Upgrade**
Budgets enforced across:

* Tokens
* Time
* Repair attempts
* Tool calls

On exhaustion → **hard fail, no retry**

---

## PHASE II — AUTONOMOUS MULTI-STEP AGENCY (5–10 STEPS)

> Goal: Long-horizon execution without drift or loops

---

### 2.1 Explicit Planning Graph (NO FREEFORM CHAINS)

**Upgrade**
Plans are delivered as **DAGs**, not text:

Each node declares:

```
intent
inputs
outputs
success_condition
timeout
rollback_action
```

**Client rejects**

* Cycles
* Undefined success conditions
* Recursive expansion without halt condition

---

### 2.2 Step-Scoped Authority

**Patch**
Backend only authorizes **one step at a time**.

Client must:

* ACK completion
* Attach verification
* Allow continuation

**Failure Mode Addressed**

* Runaway autonomy
* Silent plan mutation

---

### 2.3 Android Lifecycle-Aware Execution

**Upgrade**

* Every step checkpointed
* Resume from last verified node
* Foreground service only for active steps

**If backend continues after client death → violation**

---

## PHASE III — KNOWLEDGE SYNTHESIS & MEMORY (SERVER-SIDE)

> Goal: Learning without poisoning

---

### 3.1 Memory Stratification (MANDATORY)

**Upgrade**
Backend memory layers:

1. Working (task-scoped)
2. Episodic (execution traces)
3. Symbolic (validated facts/relations)

**Hard Rule**

* LLM may propose memory writes
* Only validators may commit

---

### 3.2 Confidence-Weighted Memory Writes

**Patch**
Every memory write includes:

* Confidence score
* Source provenance
* Verification method
* Expiry / decay

Low confidence → auto-expire

---

### 3.3 Forced Cross-Domain Synthesis

**Upgrade**
True synthesis tasks must:

* Pull from ≥2 unrelated domains
* Produce a falsifiable artifact (code, plan, test)

No artifact → no learning credit

---

## PHASE IV — CLIENT AS GOVERNOR (CRITICAL)

> Goal: Android app is the safety brake

---

### 4.1 Backend Trust Scoring

**Upgrade**
Client maintains a rolling trust score:

* Contract violations
* Hallucinated fields
* Failed verifications

Below threshold → degrade backend authority

---

### 4.2 Client-Enforced Halting Laws

**Non-negotiable**
Client hard-stops execution on:

* Max depth
* Max retries
* Budget exhaustion
* Repeated semantic failures

Backend cannot override this.

---

### 4.3 Offline / Degraded Mode

**Upgrade**
If API unavailable or untrusted:

* Read-only mode
* Cached knowledge only
* No new autonomous plans

This prevents “panic hallucination.”

---

## PHASE V — LOCAL COMPUTE (OPTIONAL, DEFENSIVE)

> Goal: Graceful degradation, not primary intelligence

---

### 5.1 Local Inference as Verifier Only

**Patch**
Local model may:

* Sanity-check API outputs
* Detect contradictions
* Estimate confidence

Local model may **not** plan autonomously unless explicitly escalated.

---

### 5.2 Thermal & Resource Awareness

**Upgrade**
Local compute auto-disables on:

* Thermal stress
* Low memory
* Background restrictions

Failures logged for synthesis upstream.

---

## PHASE VI — FAILURE ENGINEERING (MANDATORY FOR R&D)

> Goal: Find the breakpoints

---

### 6.1 Deliberate Fault Injection

**Upgrades**
Inject:

* Schema drift
* Partial responses
* Conflicting tool results
* Memory corruption

Observe:

* Repair behavior
* Loop detection
* Halting correctness

---

### 6.2 Replay & Audit

**Patch**
Every run must be:

* Replayable
* Deterministic at protocol level
* Auditable step-by-step

---

## PHASE VII — UPGRADE PATH (ONLY AFTER STABILITY)

* Multi-agent backend (Planner / Executor / Critic)
* Cross-device cooperative execution
* Model hot-swap under contract
* Formal verification hooks

---

## FINAL VERDICT

This plan turns your app into:

> **A governed agentic system where autonomy is earned, constrained, and revocable**

Not:

* A chatbot
* A thin client
* A “trust the API” app
