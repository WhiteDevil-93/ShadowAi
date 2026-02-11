# Assistant Contract Scope (Non-Negotiable)

This repository implements a full-surface Android AI Assistant client.

## Scope Guarantees
The system MUST support, without architectural limitation:

- Device integrations (telephony, SMS, accessibility, media, system controls)
- Local-first model discovery, loading, unloading, and inference
- Explicit local vs cloud routing with user/admin override
- Model attribution per message
- Admin governance mode
- Auditable decision history
- Explicit failure modes (no silent fallback)
- Expandable permission surface

## Prohibited Constraints
The following are explicitly forbidden:
- Opinionated architecture that limits future capabilities
- Hardcoded routing logic
- Forced cloud dependency
- SDK abstractions that hide execution source
- Implicit background execution

## Authority
This document supersedes:
- README simplifications
- Early skeleton assumptions
- Any “minimal app” constraints that restrict future scope
