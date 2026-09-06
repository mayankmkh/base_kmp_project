# Where the rules for this workflow come from

Master source: `docs/architecture/helix-kmp-source-of-truth.md`.

| Question | Section |
| --- | --- |
| Which failures Helix is designed to prevent, by symptom | 4. Problem diagnosis |
| Ownership, instance identity, and the three distinct keys | 12. Runtime ownership and identity |
| Snapshot / Live / Projection resources and who owns them | 14. Resource architecture |
| Why a refresh happened twice, or not at all | 15. Refresh orchestration and batching |
| Command, mutation and idempotency semantics | 16. Commands, mutations, outbox, and idempotency |
| Diagnostics and the flight recorder model | 19. Observability, diagnostics, analytics |
| What the enforcement rules check and why | 21. Mechanical architecture enforcement |

In this repository: rule IDs and their meaning are tabulated in
`build-logic/README.md`, and `./gradlew checkModuleGraph` writes
`build/reports/helix/module-graph.json`.
