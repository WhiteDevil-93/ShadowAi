# Module Boundaries

This document defines the enforced module dependency rules and how to validate them.

## Rules

- UI modules must not depend on provider implementations.
- Provider adapters must not depend on UI modules.
- All Android modules must depend on `:core-contracts`.
- Circular dependencies are forbidden.
- Dependencies must stay within the approved graph.

## Dependency Graph

```mermaid
graph TD
    core_contracts[core-contracts] --> model_catalog[model-catalog]
    core_contracts --> provider_adapters[provider-adapters]
    core_contracts --> artifact_system[artifact-system]
    core_contracts --> pipeline_planner[pipeline-planner]
    core_contracts --> ui_params[ui-params]
    core_contracts --> diagnostics[diagnostics]
    core_contracts --> hot_swapping[hot-swapping]

    model_catalog --> provider_adapters

    provider_adapters --> pipeline_planner
    artifact_system --> pipeline_planner

    ui_params --> ui_composition[ui-composition]
    pipeline_planner --> ui_composition
    artifact_system --> ui_composition

    pipeline_planner --> diagnostics

    provider_adapters --> hot_swapping

    core_contracts --> app[app]
    model_catalog --> app
    provider_adapters --> app
    artifact_system --> app
    pipeline_planner --> app
    ui_params --> app
    diagnostics --> app
    hot_swapping --> app
```

## Validation Tasks

- `./gradlew validateModuleBoundaries` validates rules and fails on violations.
- `./gradlew moduleDependencyReport` writes `build/reports/module-boundaries/module-dependency-report.txt`.
- `./gradlew moduleDependencyGraph` writes `build/reports/module-boundaries/module-dependency-graph.mmd`.
