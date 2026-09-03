# Helix KMP Agent Instructions

This repository follows **Helix KMP**. This file is the entry point for agents; it states the
laws, the vocabulary, the prohibitions, and the commands that exist **today**.

<!-- helix:generated:stage BEGIN -->
> **Adoption stage: control plane P1.** Available commands: `helix-kmp create`, `helix-kmp verify`, `helix-kmp graph`, `helix-kmp impact`, `helix-kmp doctor`, `helix-kmp context`, `helix-kmp gallery`.
<!-- helix:generated:stage END -->

## One source per fact

Every architecture fact has exactly one owner, and this file is not it:

| Fact | Single source |
| --- | --- |
| Which role may depend on which | [`config/helix/dependency-policy.json`](config/helix/dependency-policy.json) |
| Live exceptions and their expiry | [`config/helix/exceptions.json`](config/helix/exceptions.json) (schema and process: [`docs/architecture/exceptions.md`](docs/architecture/exceptions.md)) |
| Role -> plugin -> path mapping, rule IDs, tiers | [`docs/architecture/helix-adoption-plan.md`](docs/architecture/helix-adoption-plan.md) |
| Convention-plugin behaviour and the `bkpModule`/`bkpTargets` DSL | [`build-logic/README.md`](build-logic/README.md) |
| How the HTTP client is assembled | [`docs/architecture/network.md`](docs/architecture/network.md) |
| The architecture itself, in full | [`docs/architecture/helix-kmp-source-of-truth.md`](docs/architecture/helix-kmp-source-of-truth.md) |
| Why Helix, and what was rejected | [`docs/architecture/adr/0001-helix-adoption.md`](docs/architecture/adr/0001-helix-adoption.md) |

Read the policy file rather than reproducing the dependency matrix here. If this file and a source
above disagree, the source above wins and this file is the bug.

## Architecture

Four laws:

1. Explicit Ownership
2. Product-Facing Boundaries
3. Independent Lifetimes
4. Enforced Evolution

Ownership card:

```
One state       -> one owner
One resource    -> one owner
One instance    -> one identity
One arch fact   -> one source of truth
```

## Canonical vocabulary

- **Feature** = product presentation scope.
- **Screen** = navigable presentation destination.
- **Cell** = independently hostable stateful presentation unit owned by a Feature; not a base class
  and not a Gradle module. Its signature is `(id, instanceKey: FeatureInstanceKey, onOutput)`.
- **UI** = stateless reusable rendering.
- **Capability** = reusable business/product Queries + Commands + stable models.
- **Resource** = Snapshot, Live, or Projection, owned below presentation.

## Module roles

One module has exactly one role, declared by applying exactly one `bkp.kmp.*` role plugin. The
path and the role must agree (`MOD-PATH-ROLE-MISMATCH`).

<!-- helix:generated:module-roles BEGIN -->
| Path | Role | What lives there |
| --- | --- | --- |
| `app/*` | `app` | Composition roots and platform entry points |
| `feature/*` | `feature` | Screens, Cells, ViewModels, Outputs |
| `ui/*` | `ui` | Stateless rendering only |
| `capability/*-api` | `capability_api` | Queries, Commands, product models |
| `capability/*-impl` | `capability_impl` | Internal implementation; public Koin module only |
| `foundation/*` | `foundation_api` | Cross-cutting contracts |
| `foundation/*` | `foundation_runtime` | Cross-cutting runtime |
| `platform/*` | `platform` | Platform and OS seams |
| `platform/*-api` | `platform_api` | Platform seam contracts |
| `platform/*-impl` | `platform_impl` | Platform seam implementations |
| `storage/*` | `storage` | Assembles schemas and verifies merged database migrations |
| `testkit/*` | `testkit` | Test-only fakes and fixtures |
<!-- helix:generated:module-roles END -->

## Required workflow

Prefer the CLI and the workflow Skills in [`.agents/skills/`](.agents/skills/) over hand-created
architecture wiring.

<!-- helix:generated:workflow BEGIN -->
```bash
tooling/helix-kmp/helix-kmp context <target>
tooling/helix-kmp/helix-kmp create feature <name> [--capability <name>]
tooling/helix-kmp/helix-kmp create capability <name>
tooling/helix-kmp/helix-kmp create cell <feature> <CellName>
tooling/helix-kmp/helix-kmp verify --fast --affected
tooling/helix-kmp/helix-kmp impact <target>
tooling/helix-kmp/helix-kmp doctor [<scope>] --explain
tooling/helix-kmp/helix-kmp graph [<module>]
tooling/helix-kmp/helix-kmp gallery
```
<!-- helix:generated:workflow END -->

Use `--dry-run` for create planning. Graph-backed commands refresh the report unless
`--no-refresh` is explicitly supplied. The equivalent verification entry points are
`./gradlew verifyFast` and `./gradlew verifyFull`.

Skills, all under `.agents/skills/<name>/SKILL.md`:

| Skill | Use it for |
| --- | --- |
| `build-feature` | Normal product work inside an existing boundary |
| `evolve-architecture` | Moving, splitting or merging a boundary |
| `debug-and-repair` | Lifecycle, state-loss, duplicate-resource and DI bugs |
| `review-and-verify` | Reviewing a change and producing evidence |

When a rule fails, read the finding. Every finding is formatted
`[RULE-ID] subject -- problem. Fix: remedy` and names the fix. Do not bypass the rule by importing
implementation types, widening a public API for convenience, or adding a global manager or service
locator.

## High-value prohibitions

<!-- helix:generated:policy-prohibitions BEGIN -->
- Feature -> Capability Impl: forbidden.
- Capability Impl -> another business Capability Impl: forbidden.
- UI -> Feature / Capability API / Capability Impl: forbidden.
- Capability API -> UI / Capability Impl / Foundation Runtime / Storage: forbidden.
<!-- helix:generated:policy-prohibitions END -->

- UI contains no ViewModel, Koin, navigation, or repository code.
- Capability API contains no Compose, network, DB, Store5, or implementation code.
- Peer Cell implementation -> peer Cell implementation: forbidden.
- A Feature's public surface lives in its `api` package only (`FEATURE-PUBLIC-SURFACE-OUTSIDE-API`).
- A `UiCommand` must not carry correctness-bearing domain or resource state.
- `RouteKey` != `FeatureInstanceKey` != `ResourceKey`.

An exception to any of these is a registry entry with an owner, a reason and an expiry date, not a
comment. See [`docs/architecture/exceptions.md`](docs/architecture/exceptions.md).

## Protected work

Escalate for explicit experienced review when changing authentication, payments,
privacy or security, irreversible migrations, concurrency or shared-resource lifecycle
infrastructure, public Capability APIs, or the architecture rules and control plane themselves.

## Verification

A change is not complete until the verification tier for its blast radius is green:

| Tier | Command | Covers |
| --- | --- | --- |
| Fast | `tooling/helix-kmp/helix-kmp verify --fast --affected` | Changed modules: JVM tests, `detektAll`, `spotlessCheck`, plus `checkModuleGraph` and `checkHelixPolicySync` |
| Fast, whole repo | `./gradlew verifyFast` | Every module's JVM/common tests and quality gates |
| Full | `./gradlew verifyFull` | Fast, plus the Android debug APK, the web bundles and the iOS simulator framework |

Never run a bare `./gradlew build`: it builds fourteen production web bundles and exhausts memory.
Use `check` plus the targeted assembles above.

The control plane has its own test, which is deliberately **not** part of `verifyFast` because it
mutates `settings.gradle.kts` and runs Gradle recursively. Run it after changing the CLI or a
template:

```bash
tooling/helix-kmp/tests/run-tests.sh
```

## What does not exist yet

Commands in later control-plane stages remain unavailable:

<!-- helix:generated:future-commands BEGIN -->
| Command | Stage | Status |
| --- | --- | --- |
| `helix-kmp extract ...` | P2 | Not built |
| `helix-kmp migrate ...` | P2 | Not built |
<!-- helix:generated:future-commands END -->
