# CI Agent Model

CI workflows in this repository are treated as autonomous agents.
Each agent has a single responsibility and produces auditable artifacts.

## Agent Pipeline
1. Inventory Agent
2. Toolchain Contract Enforcer
3. Dependency Governance Agent
4. Build Verification Agent
5. Test Verification Agent (future)

## State Transfer
Agents communicate only via artifacts.
No hidden or mutable state is allowed.

## Control Plane
- Humans and mobile clients trigger workflows.
- CI is the execution authority.
