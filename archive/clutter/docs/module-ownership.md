# Module Ownership

This document assigns ownership for each module to keep boundaries clear.

| Module            | Owner                 | Responsibilities                                           |
| ----------------- | --------------------- | ---------------------------------------------------------- |
| core-contracts    | Core Platform         | Shared contracts, ABI stability, dependency root rules.    |
| model-catalog     | Model Platform        | Model discovery, validation, deduplication.                |
| provider-adapters | Provider Integrations | Provider APIs, auth, resiliency, adapter tests.            |
| artifact-system   | Pipeline Platform     | Artifact storage, validation, conversion.                  |
| pipeline-planner  | Pipeline Platform     | Pipeline graphs, execution, caching, transforms.           |
| ui-params         | UI Platform           | Dynamic parameter rendering and validation.                |
| ui-composition    | UI Platform           | Transformation views, composition rules, UI orchestration. |
| diagnostics       | Reliability           | Error taxonomy, recovery, observability UI.                |
| hot-swapping      | Reliability           | Config loading, migration, runtime swap safety.            |
| app               | App Experience        | Navigation, screens, DI, integration of modules.           |
| backend           | Backend Services      | Ktor services, image generation APIs.                      |
