# Helix KMP Agent Instructions

This repository follows **Helix KMP**. This file is the entry point for agents; it states the
laws, the vocabulary, the prohibitions, and the commands that exist **today**.

> **Adoption stage: control plane P0.** `helix-kmp create` and `helix-kmp verify` exist. The rest
> of the command family described in the master source (Section 22) does **not** exist in this
> repository yet -- see [What does not exist yet](#what-does-not-exist-yet). Do not invent it, do
> not write instructions that assume it, and do not claim its output.

## One source per fact

Every architecture fact has exactly one owner, and this file is not it:

| Fact | Single source |
| --- | --- |
| Which role may depend on which | [`config/helix/dependency-policy.json`](config/helix/dependency-policy.json) |
| Live exceptions and their expiry | [`config/helix/exceptions.json`](config/helix/exceptions.json) (schema and process: [`docs/architecture/exceptions.md`](docs/architecture/exceptions.md)) |
| Role -> plugin -> path mapping, rule IDs, tiers | [`docs/architecture/helix-adoption-plan.md`](docs/architecture/helix-adoption-plan.md) |
| Convention-plugin behaviour and the `bkpModule`/`bkpTargets` DSL | [`build-logic/README.md`](build-logic/README.md) |
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

| Path | Role | What lives there |
| --- | --- | --- |
| `app/*` | `app` | Composition roots and platform entry points |
| `feature/*` | `feature` | Screens, Cells, ViewModels, Outputs |
| `ui/*` | `ui` | Stateless rendering only |
| `capability/*-api` | `capability_api` | Queries, Commands, product models |
| `capability/*-impl` | `capability_impl` | The implementation, all `internal` bar its Koin module |
| `foundation/*` | `foundation_api` / `foundation_runtime` | Cross-cutting contracts and runtime |
| `platform/*` | `platform` | Platform/OS seams |
| `storage/*` | `storage` | Databases (product schema shared across Capabilities) |
| `testkit/*` | `testkit` | Test-only fakes and fixtures |

## Required workflow

Prefer the CLI and the workflow Skills in [`.agents/skills/`](.agents/skills/) over hand-created
architecture wiring.

```bash
tooling/helix-kmp/helix-kmp create feature <name> [--capability <name>]
tooling/helix-kmp/helix-kmp create capability <name>
tooling/helix-kmp/helix-kmp create cell <feature> <CellName>
tooling/helix-kmp/helix-kmp verify --fast --affected
tooling/helix-kmp/helix-kmp verify --full
```

Add `--dry-run` to any of them to see what would happen without touching the tree. The equivalent
Gradle entry points are `./gradlew verifyFast` and `./gradlew verifyFull`.

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

- Feature -> Capability Impl: forbidden.
- Capability Impl -> another business Capability Impl: forbidden.
- UI -> ViewModel / Koin / navigation / Capability / repository: forbidden.
- Capability API -> Compose / network / DB / Store5 / impl: forbidden.
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

These commands are described by the master source and are **not implemented** here. They are the
P1 and P2 stages of the control plane (master source Section 22.1.1). No part of them exists: no
stub, no partial output, no JSON schema.

| Command | Stage | Status |
| --- | --- | --- |
| `helix-kmp graph` | P1 | Not built |
| `helix-kmp impact <target>` | P1 | Not built |
| `helix-kmp doctor <scope> --explain` | P1 | Not built |
| `helix-kmp context <target>` | P1 | Not built |
| `helix-kmp gallery` | P1 | Not built |
| Generated / no-drift-checked agent instructions and Skills | P1 | Not built; this file and the Skills are written and maintained by hand |
| `helix-kmp extract ...` | P2 | Not built |
| `helix-kmp migrate ...` | P2 | Not built |

Where a workflow in the master source calls for `context`, `impact` or `doctor`, do the equivalent
by hand: read the module's `build.gradle.kts` and its `api` package, read
`build/reports/helix/module-graph.json` after `./gradlew checkModuleGraph`, and grep for consumers
of the type you are about to change.
