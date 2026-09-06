# Helix KMP Master Architecture Source of Truth

**Architecture:** Helix KMP  
**Canonical baseline:** Helix KMP Baseline v1  
**Master source edition:** 1.3  
**Snapshot date:** 2026-09-02  
**Status:** Helix KMP Baseline v1 remains design-frozen at the four-law/core-boundary level; Master 1.3 is a correctness/no-drift pass over the 1.2 implementation-closure revision. Core runtime/data seams remain POC-qualified; early real-feature ergonomics remain subject to evidence-driven refinement.  
**Public CLI:** `helix-kmp`  
**Canonical source format:** Markdown  

> This file is the single comprehensive source from which all Helix KMP architecture, implementation, management, QA, contribution, AI-tooling, ADR, adoption, glossary, and HTML documentation may be derived.
>
> If a derived document conflicts with this file, this file wins unless this file is explicitly superseded by a later approved master edition.

---

## Master navigation

This file is intentionally exhaustive. Use this map to jump to the level of detail needed.

- **Foundations and reasoning:** [0. How to read this master](#0-how-to-read-and-maintain-this-master) · [1. Executive definition](#1-executive-definition) · [2. Four laws](#2-the-four-canonical-helix-laws) · [3. Design qualities](#3-derived-design-qualities-and-management-outcomes) · [4. Problem diagnosis](#4-problem-diagnosis-what-helix-is-designed-to-prevent) · [5. Design history and alternatives](#5-architecture-design-history-and-alternatives-explored) · [6. Architecture at a glance](#6-final-architecture-at-a-glance)
- **Core application architecture:** [7. Vocabulary](#7-canonical-vocabulary) · [8. Module taxonomy](#8-physical-module-taxonomy) · [9. Dependency direction](#9-dependency-direction) · [10. Reuse/extraction](#10-reuse-and-extraction-model) · [11. Presentation](#11-presentation-architecture) · [12. Runtime/identity](#12-runtime-ownership-and-identity) · [13. Capability API](#13-capability-api-and-business-model) · [14. Resources](#14-resource-architecture) · [15. Refresh/batching](#15-refresh-orchestration-and-batching) · [16. Commands/outbox](#16-commands-mutations-outbox-and-idempotency) · [17. Storage](#17-durable-storage-and-database-topology) · [18. Libraries/mechanisms](#18-technical-mechanisms-and-current-library-choices)
- **Quality and control plane:** [19. Observability](#19-observability-diagnostics-analytics-and-the-flight-recorder) · [20. Testing](#20-testing-and-quality-architecture) · [21. Mechanical enforcement](#21-mechanical-architecture-enforcement) · [22. CLI/scripts/AI Skills/context](#22-the-helix-kmp-control-plane) · [23. Evolution/doctor](#23-architecture-evolution-and-doctor) · [24. ADR governance](#24-adr-and-qualification-governance) · [25. ADR catalog](#25-current-adr-catalog) · [26. Qualification/evidence](#26-qualification-snapshot-and-evidence-ledger) · [27. Costs/risks](#27-known-costs-risks-and-honest-counterpoints)
- **Adoption, audiences, and documentation:** [28. Naming](#28-naming-and-public-identity) · [29. Starter adoption](#29-one-time-adoption-model-for-the-existing-kmp-starter) · [30. Cricket reference slice](#30-canonical-cricket-reference-slice) · [31. Non-mobile contribution](#31-contribution-without-android-expertise) · [32. QA model](#32-qa-operating-model) · [33. Management metrics](#33-management-outcomes-and-metrics) · [34. Derived-document rules](#34-documentation-derivation-rules)
- **Guardrails and reference:** [35. Red flags](#35-architecture-red-flags) · [36. Non-goals](#36-non-goals) · [37. External influences](#37-external-architecture-and-ecosystem-influences-explored) · [38. Public references](#38-public-reference-urls-used-for-qualificationreasoning) · [39. Compact mental model](#39-final-compact-mental-model) · [40. Maintenance checklist](#40-master-maintenance-checklist) · [41. Closing statement](#41-closing-architecture-statement)
- **Reusable source material:** [Appendix A - AGENTS.md](#appendix-a-full-root-agentsmd-template) · [Appendix B - AI Skills](#appendix-b-full-workflow-skill-templates) · [Appendix C - CLI workflows](#appendix-c-detailed-public-cli-workflow-examples) · [Appendix D - Why this / why not that](#appendix-d-decision-matrix-why-this-and-why-not-that) · [Appendix E - Rejected historical ideas](#appendix-e-historical-ideas-explicitly-not-part-of-current-helix) · [Appendix F - Provenance](#appendix-f-source-provenance)

## Current decision snapshot

This table is a quick orientation only. The referenced sections contain the reasoning, evidence, alternatives, and revisit conditions.

| Area | Current Helix KMP position | Classification |
|---|---|---|
| architecture unit | Feature owns Screens and logical Cells; Cell is not a base class or module | normative |
| module strategy | fixed small role taxonomy; start cohesive; extract/merge from evidence | normative |
| presentation | AndroidX ViewModel + UDF; State/Action required, UiCommand/Output optional | current default behind normative ownership rules |
| navigation | Navigation 3 for Screen destinations | current qualified choice |
| embedded smart lifetime | narrow keyed ViewModel owner keyed by FeatureInstanceKey | current qualified choice |
| business reads | grouped Capability Query interfaces | normative convention |
| writes | intent-shaped Commands | normative convention |
| resource model | Snapshot / Live / Projection | normative |
| Snapshot implementation | owned `SyncCoordinator` + SQLDelight + REST where appropriate | current replaceable choice |
| Live implementation | capability-owned keyed coordinator + shared resource state + WebSocket | normative ownership; mechanism replaceable |
| durable storage | SQLDelight; one physical DB is default, not invariant | current qualified choice |
| refresh | common external triggers/QoS; Capability owns freshness/retry/invalidation | normative responsibility split |
| batching | BatchHydrator may feed resource owners but never become observable state/query API | normative |
| DI | Koin + constructor injection + graph/compiler verification | current replaceable choice |
| observability | FeatureObserver + ProductAnalytics semantic seams; Kermit default; adapters optional | normative seam + current adapter choice |
| dynamic composition | registry only when product can independently change presence/order/variant | normative decision rule |
| GraphQL | deliberately deferred; backend is REST-oriented; future implementation remains possible behind Capability API | current product constraint |
| architecture tooling | stable `helix-kmp` CLI; deterministic replaceable scripts; graph-first facts | normative control-plane contract |
| AI operating model | few workflow Skills + generated context + same verification/permissions as humans | normative operating model |
| synchronized Query status | remotely synchronized reads expose a transport-neutral `ResourceObservation<T>` when loading/refresh/error matters | normative Capability contract |
| coroutine ownership | App owns the application runtime parent scope; Capability resource owners create/cancel child jobs; never borrow ViewModel scope for shared resources | normative lifetime rule |
| session/auth | Identity owns session/token refresh; Foundation network owns generic bearer/401 mechanism; terminal session end is a typed cross-cutting fact | normative ownership split |
| verification | fast affected tier for human/agent inner loop; full supported-target matrix in CI/release/qualification | normative operating contract |
| primitive policy | standard/framework primitive first; every bespoke Helix primitive needs a complete executable reference + regression + delete/revisit condition | normative design rule |
| documentation | this Markdown master is reasoning/source-of-truth; audience docs and HTML are derived | normative documentation governance |

# 0. How to read and maintain this master

## 0.1 Why this file exists

The architecture was developed through several rounds of design, POCs, adversarial reviews, alternative runtime/data proposals, library qualification, and documentation refinement. Smaller audience-specific documents are intentionally easier to read, but they omit history and detail.

This master preserves all of the following in one place:

- the problem Helix KMP is solving;
- the final architecture and its invariant rules;
- the design qualities and management outcomes it optimizes for;
- the full vocabulary and ownership model;
- module and dependency rules;
- presentation, runtime, data, refresh, persistence, observability, testing, and platform decisions;
- the AI/control-plane architecture: CLI, deterministic scripts, Skills, context packets, agent instructions, codemods, architecture graph, `doctor`, `impact`, and migration recipes;
- the libraries and architectures explored;
- why alternatives were selected, rejected, deferred, or kept as escape hatches;
- internal POC evidence and dependency qualification status;
- known costs and criticisms;
- explicit `Revisit when` conditions;
- the one-time adoption model for a lightly configured KMP starter;
- naming history and public-name constraints;
- rules for deriving concise management, QA, technical, and contribution documents.

The master is deliberately exhaustive. Derived documents should be shorter.

## 0.2 Information classes

Every important statement in this file belongs to one of four classes.

### NORMATIVE

A Helix KMP architectural rule. Examples:

- Feature must not depend on Capability implementation.
- `RouteKey`, `FeatureInstanceKey`, and `ResourceKey` are distinct identities.
- A Cell is not a mandatory base class or Gradle module.

Changing a normative rule is an architecture change and requires an ADR/update to this master.

### CURRENT IMPLEMENTATION CHOICE

A selected technology or mechanism behind a stable Helix boundary. Examples:

- Navigation 3;
- Koin;
- owned Snapshot resource runtime;
- SQLDelight;
- Kermit;
- Ktor.

These are not Helix's identity. They may be replaced when qualification or `Revisit when` evidence justifies it.

### QUALIFICATION / EVIDENCE

POC results, regression suites, version pinning, target support, and empirical metrics. Qualification can change without changing the architecture laws.

### HISTORICAL / ALTERNATIVE

An explored design that explains why the final architecture looks the way it does. Historical content is retained for reasoning, not implementation.

Examples:

- universal Cell `Loading | Content | Error | Hidden` state;
- presenter-composable Cells;
- direct `QueryStore` access from presentation;
- one global `RefreshCoordinator` owning freshness policy;
- module-per-Cell API/implementation pairs.

## 0.3 Canonical authority hierarchy

```text
Helix_KMP_Master_Source_of_Truth.md
        |
        +--> derived technical Markdown
        +--> derived management/QA/contribution Markdown
        +--> derived ADR/qualification pages
        +--> derived adoption runbook
        |
        +--> generated HTML documentation site
        +--> optional PDF/DOCX release snapshots
```

Do not independently edit multiple copies of the same architectural fact.

## 0.4 One source per architecture fact

| Fact | Canonical operational source |
|---|---|
| architecture meaning/rationale | this master source |
| module role | convention plugin and module path |
| project dependencies | actual Gradle graph |
| platform targets | KMP target/source-set configuration |
| scope/domain working set | naming convention; rare central override |
| ownership | CODEOWNERS or central ownership system |
| Kotlin public API | source declarations + ABI tooling |
| dependency qualification | central qualification register derived from this master + current evidence |
| architecture exceptions | central expiring exception registry |
| architecture index/context | generated from repository graph, never hand-maintained |
| role-to-role dependency matrix | `config/helix/dependency-policy.json`; this master explains it |
| control-plane stage contract | `config/helix/control-plane-stages.json`; this master explains it |

The master explains these facts; it must not cause teams to duplicate them in per-module YAML or custom architecture DSLs.

### 0.4.1 Normative duplication policy

The master is intentionally exhaustive, but **exhaustive does not mean multiple authorities**.

Rules:

- every normative architectural fact has one canonical normative section or one canonical machine-readable policy file;
- ADRs, history, quick snapshots, management summaries, `AGENTS.md`, Skills, and derived documents may repeat a rendering of the fact, but they are not independent authorities;
- repeated prose must reference the canonical fact and, once the relevant generator/checker exists, must be generated or no-drift checked from it;
- graph validation, documentation generation, agent instructions, and CLI error messages should consume the same canonical policy inputs where practical;
- if two copies disagree, treat that as a documentation/build defect.

**Implementation-status rule:** the master must not call a repeated artifact "generated" merely because generation is planned. Until a generator/no-drift checker actually exists in the repository, the repeated artifact is a **specified derived copy** and its unautomated sync is tracked as control-plane debt. P1 adoption requires installing the no-drift generation/check path for root agent instructions and other mechanically derivable policy summaries. Machine-read policy data is not rendered here at all: the dependency matrix and the control-plane stage contract live in `config/helix/*.json`, which the build and the CLI read directly, so there is no second copy to keep in sync.

This lets the master remain exhaustive while normal agents consume smaller derived views.

## 0.5 Change-control rule

A proposed architecture change should answer:

1. Which current rule/decision is changing?
2. Which evidence or `Revisit when` condition fired?
3. What is the expected reduction in change size, risk, complexity, or owned infrastructure?
4. What deterministic migration recipe is available?
5. Which POC/regression suites prove required behavior?
6. Which derived documents, generators, rules, Skills, and architecture-kit version must change?

Architecture fashion by itself is not evidence.

## 0.6 Document map

This master is the normative core: the laws, the vocabulary, the module taxonomy and dependency
direction, the presentation and runtime ownership rules, the Capability and Resource contracts, the
current technical mechanisms with their qualification evidence, enforcement, the control plane, ADR
governance, naming, red flags and non-goals. The history, the essays, the audience guides, the
worked reference slice, the ADR entries and the copyable templates live in companion files. Each
companion is a rendering of this master's reasoning, never an independent authority.

| Companion | What it holds | Moved from |
|---|---|---|
| [`decisions.md`](decisions.md) | the ADR catalog, one `ADR-NN` heading per decision | 25 |
| [`reference-slice.md`](reference-slice.md) | the canonical Cricket reference slice, end to end | 30 except 30.4 |
| [`background.md`](background.md) | management metrics, external influences, public reference URLs | 33, 37, 38 |
| [`templates/agents-and-skills.md`](templates/agents-and-skills.md) | copyable root `AGENTS.md` and workflow Skill templates | Appendix A, Appendix B |
| [`../history/helix-design-history.md`](../history/helix-design-history.md) | how the architecture was arrived at, the alternatives, the critiques, the provenance | 5, Appendix F |
| [`../history/helix-adoption-model.md`](../history/helix-adoption-model.md) | the retired one-time starter adoption model | 29 |
| [`../guides/contributing-without-android-expertise.md`](../guides/contributing-without-android-expertise.md) | bounded contribution without platform expertise | 31 |
| [`../guides/qa-operating-model.md`](../guides/qa-operating-model.md) | the QA mental model and workflow | 32 |
| [`../guides/documentation-derivation.md`](../guides/documentation-derivation.md) | how audience documents are derived from this master | 34 |

Repository-local companions that were never part of this master, and remain their own sources:
[`adr/`](adr/) for local ADRs, [`exceptions.md`](exceptions.md),
[`network.md`](network.md), [`preferences.md`](preferences.md),
[`helix-adoption-plan.md`](helix-adoption-plan.md),
[`helix-adoption-evidence.md`](helix-adoption-evidence.md), and
[`../../build-logic/README.md`](../../build-logic/README.md).

---

# 1. Executive definition

## 1.1 Formal definition

> **Helix KMP organizes presentation instances, business capabilities, resources, and durable state around explicit owners, while an automated control plane keeps boundaries consistent and evolves them from measurable evidence.**

A shorter public descriptor is:

> **A modular ownership architecture for Kotlin Multiplatform.**

An optional expanded descriptor is:

> **A modular ownership architecture for AI-first Kotlin Multiplatform applications.**

AI-first does not mean AI-dependent or autonomous-by-default. It means the architecture is intentionally legible, bounded, and mechanically operable by both humans and coding agents.

## 1.2 Product context that shaped the design

The architecture targets a substantial client product with:

- Kotlin Multiplatform;
- Compose Multiplatform;
- Android, iOS, Desktop, and Web/Wasm targets;
- feed/dashboard-style surfaces containing mostly simple content plus a minority of autonomous stateful widgets;
- reusable business behavior that may be consumed by multiple presentations;
- reusable visuals that may have different behavior in different places;
- multiple independent instances of the same stateful widget;
- shared expensive resources such as WebSockets, media players, batch queues, caches, and databases;
- REST-oriented backend APIs today;
- no expected GraphQL backend support in the near future;
- human, QA, backend/web, and AI-agent contribution;
- a desire to reduce dependence on deep Android-specific knowledge for ordinary product work.

The repository being adopted is a lightly configured KMP starter, not a mature legacy monolith and not an empty greenfield repository.

## 1.3 The primary architecture test

> **A boundary is good when it makes the expected change smaller.**

A visually elegant graph is not useful if a normal change requires:

- more modules;
- more public API;
- more context;
- more rebuilding;
- more tests;
- more owners;
- more coordination;
- or more architectural decisions.

This test governs extraction, module count, reuse, runtime abstraction, and control-plane investment.

---

# 2. The four canonical Helix laws

Helix has exactly four canonical laws. Do not create a second competing list of "principles" with equivalent normative status.

## 2.1 Law 1 - Explicit Ownership

Every meaningful state, resource, lifecycle, shared mutable runtime, and architecture fact has one authoritative owner.

Examples:

- a Screen/Cell ViewModel owns local presentation state;
- a Capability owns shared business/resource state;
- a keyed live-resource coordinator owns one shared socket lifecycle;
- Navigation 3 owns route/back-stack state;
- SQLDelight owns durable relational state;
- CODEOWNERS owns review ownership facts;
- Gradle owns actual dependency facts.

Avoid duplicated mutable truth.

## 2.2 Law 2 - Product-Facing Boundaries

Presentation and consumers speak product language rather than implementation mechanism language.

Prefer:

```kotlin
cricketQueries.liveScore(matchId)
bookmarkArticle(articleId)
```

Avoid presentation-facing contracts such as:

```kotlin
store.observe(ResourceKey.MatchScore(matchId))
database.matchQueries.select(...)
httpClient.get("/match/...")
```

Mechanisms may change. Product semantics should remain stable.

## 2.3 Law 3 - Independent Lifetimes

Different identities and state categories have different lifetimes and must not be accidentally tied together.

A route, a Cell instance, a shared match resource, a WebSocket, and an offline command do not have the same owner or lifetime.

## 2.4 Law 4 - Enforced Evolution

Architecture is generated, checked, diagnosed, measured, and migrated through tooling wherever practical.

The goal is:

```text
generate > compile-check > architecture-test > CI-check > document > hope
```

Documentation explains the architecture. Machines should enforce facts they can know.

## 2.5 Ownership card

```text
One state       -> one owner
One resource    -> one owner
One instance    -> one identity
One arch fact   -> one source of truth
```

---

# 3. Derived design qualities and management outcomes

These are outcomes/heuristics produced by the four laws. They are not additional laws.

## 3.1 Low blast radius

A conceptually small product change should normally touch a small, predictable working set.

Blast radius includes:

- source files changed;
- modules rebuilt;
- tests rerun;
- public APIs affected;
- teams/owners involved;
- agent context loaded;
- runtime qualification suites affected.

Surprising broad impact from a local product change is architecture feedback.

```mermaid
flowchart LR
    CR[Change request] --> OW[One explicit owner]
    OW --> SRC[Small source/test set]
    SRC --> IMP[Graph-derived impact]
    IMP --> VER[Only justified checks + owners]
    VER --> PR[Reviewable PR]
```

## 3.2 High cohesion

Code that changes for the same product reason stays together.

Examples:

- a Cell keeps its presentation behavior, state, fixtures, and tests together as complexity warrants;
- a Capability groups related product reads, commands, resource semantics, and hidden data implementation;
- pure UI rendering lives together without unrelated business logic.

High cohesion is not an excuse for a giant Feature. It means the current boundary contains things that belong together.

## 3.3 Low coupling

Unrelated areas know as little as possible about each other's internals.

Key mechanisms:

- Feature -> Capability API, never Capability Impl;
- peer Cells do not import one another's implementations;
- UI modules know no ViewModel/DI/navigation/business resource details;
- Capability implementations depend on other Capability APIs, not implementations;
- platform/vendor types stop behind stable seams.

## 3.4 Modular, not fragmented

Modules are compilation/ownership walls, not trophies.

Helix starts cohesive and physically extracts when evidence says a wall reduces expected change size.

Strong extraction signals include:

- second independent consumer;
- second owning team;
- stable API with volatile heavy implementation;
- incoming ABI/dependency fanout;
- build/test fanout;
- context-surface pressure;
- repeated illegal-edge attempts;
- low Git co-change between bundled areas.

Merge is also valid when fragmentation creates cost without isolation value.

## 3.5 Reusable by responsibility

There are three intentionally different reuse lanes.

| Reuse need | Helix home |
|---|---|
| pure visual rendering | `:ui:*` |
| reusable business/data behavior | `:capability:*` |
| complete autonomous stateful presentation | Feature-owned Cell/public presentation entry |

Do not force all reuse through one abstraction.

## 3.6 AI-agent friendly, not AI-dependent

Finite-context coding agents benefit when a task can be understood from one cohesive graph slice.

Helix therefore optimizes for:

- explicit ownership;
- bounded public APIs;
- generated context packets;
- deterministic Skills/CLI workflows;
- local fixtures/tests;
- architecture checks with repair guidance.

Context-window fit is useful but is not the sole reason for the architecture. If future agents safely comprehend whole repositories, low blast radius, high cohesion, low coupling, testability, explicit ownership, and deterministic verification still remain valuable.

## 3.7 Context-window fit

A normal task should usually fit:

```text
target implementation + target tests
+ direct dependency public APIs
+ applicable architecture rules
+ fixtures/acceptance examples
+ exact verification commands
```

Do not load sibling implementations or distant transitive internals by default.

Context thresholds are organization tuning parameters, not Helix laws.

## 3.8 Simple by default; complexity is earned

Examples:

- plain content: immutable model -> Composable;
- independently hostable stateful presentation: Cell;
- navigable destination: Screen;
- reducer/state machine: only for workflows that genuinely need one;
- UiCommand/Output: optional;
- registry: only on independently composable/dynamic surfaces;
- physical module extraction: evidence-driven.

## 3.9 Predictable contribution

Humans and agents should not invent module shapes or wiring for routine work.

Normal loop:

```text
intent
  -> workflow Skill
  -> graph-derived context
  -> generator/edit
  -> deterministic verification
  -> impact/evidence
  -> review
```

## 3.10 Deterministic feedback

Prefer fast, local, machine-verifiable failures over broad runtime surprises.

Examples:

- illegal dependency -> graph validator;
- forbidden import/public exposure -> architecture test;
- missing DI graph -> compiler/graph validation;
- public API change -> ABI diff;
- lifetime bug -> keyed-owner/nav regression test;
- visual change -> fixture/golden diff;
- resource duplication -> inspector + live-resource test.

## 3.11 Observable ownership

Debug/QA tooling should answer:

- Which Screen/Cell instance is this?
- Which ResourceKey does it observe?
- How many observers/subscribers exist?
- What owns the live connection?
- What is the freshness state?
- What wrote the resource last?
- Why did refresh run?
- What command/outbox work is pending?

## 3.12 Replaceable technology

Navigation 3, Koin, the owned Snapshot resource runtime, SQLDelight, Ktor, Coil, WebSocket implementation, and telemetry adapters are qualified choices, not Helix identity.

## 3.13 Evidence-driven evolution

Architecture changes happen because evidence fires, not because a reviewer prefers a pattern.

Signals include:

- second consumer/team;
- public API churn/fanout;
- compile/test impact;
- context size;
- Git co-change;
- duplicated resource logic;
- DB/schema pressure;
- repeated exceptions;
- owned-infrastructure complexity;
- ADR revisit condition.

---

## 3.14 Standard primitive first

Prefer mechanisms already understood by the Kotlin/Compose/KMP ecosystem before creating Helix-owned runtime abstractions.

Good defaults include ordinary Kotlin types, AndroidX ViewModel, `StateFlow`/`Flow`, Ktor, SQLDelight, Compose primitives, Gradle/module visibility, and standard KMP `expect/actual` where it solves the platform seam directly.

A bespoke Helix primitive is justified only when a concrete requirement is not met cleanly by available standard/framework mechanisms.

Before a bespoke primitive becomes a baseline dependency, it must have:

1. one clearly stated missing requirement;
2. the smallest practical API surface;
3. one complete executable usage in the canonical Cricket/reference slice;
4. regression tests for the behavior that justified it;
5. diagnostics good enough that humans and agents can repair failures;
6. a `Revisit when` / deletion condition.

This applies especially to keyed embedded presentation ownership, FeatureObserver helpers, Resource Inspector hooks, Helix CLI schemas, and graph/doctor conventions.

## 3.15 How the qualities connect to delivery outcomes

```mermaid
flowchart LR
    O[Explicit owners] --> C[Higher cohesion]
    O --> L[Lower coupling]
    C --> B[Smaller expected change]
    L --> B
    B --> R[Lower blast radius]
    R --> F[Faster verification and review]
    R --> D[Fewer unrelated regressions]
    G[Graph + generators + deterministic rails] --> P[Predictable contribution]
    P --> F
    X[Bounded context packets] --> A[Agent/human context efficiency]
    A --> F
    E[Evidence-driven evolution] --> M[Boundaries improve instead of calcify]
    M --> B
```

Management value is not "more modules." The intended outcome is **smaller, safer, easier-to-understand changes with reusable ownership and faster evidence**.

---

# 4. Problem diagnosis: what Helix is designed to prevent

## 4.1 Screen-scoped God ViewModels

Typical failure:

```text
HomeScreen
  -> HomeViewModel
       - article state
       - cricket state
       - recommendations state
       - video coordination
       - refresh timers
       - navigation decisions
       - analytics
       - sockets
```

Use cases alone fix data-call layering but do not necessarily fix state/coordination ownership. The screen remains the aggregation point.

## 4.2 Stateful widget reuse tied to a screen

A complex widget in a feed talks to the screen ViewModel. Moving it to another surface requires:

- copying ViewModel logic;
- widening the parent ViewModel;
- creating ad-hoc state plumbing;
- or introducing feature-to-feature internal dependencies.

Helix gives autonomous presentation a Cell boundary when independent hosting/lifetime is valuable.

## 4.3 State survives accidentally because it sits in a screen ViewModel

Resource durability should not be an accidental side effect of navigation lifetime.

Shared resource state belongs below presentation. Presentation state belongs to its logical Screen/Cell owner.

## 4.4 Same widget twice causes identity collision

If two instances are keyed only by screen/type/business entity, local expansion/selection state can collide.

Helix separates:

- presentation instance identity;
- resource identity;
- route identity.

## 4.5 N widgets cause N network calls or ad-hoc batching

Fetching must not be coupled to visual placement. Batch/BFF optimization belongs below stable capability/resource boundaries.

## 4.6 Shared live resources duplicate connections

A WebSocket opened by each Cell/ViewModel creates duplicate resource ownership. Helix gives live resources a keyed capability-owned coordinator.

## 4.7 Architecture rules live only in senior engineers' heads

This fails for:

- new hires;
- cross-stack contributors;
- QA automation engineers;
- AI agents;
- high parallelism.

Helix moves conventions into generation, graph rules, tests, CLI workflows, Skills, and migration recipes.

## 4.8 AI amplifies old architecture weaknesses

AI did not create God objects, hidden coupling, or weak ownership. It changes their cost weighting:

- ambiguous ownership leads agents to plausible but wrong locations;
- huge working sets consume context and reduce accuracy;
- duplicated conventions drift faster when code generation volume rises;
- runtime-only failures create slow agent repair loops.

The response is not "make every file tiny." The response is explicit cohesive boundaries plus deterministic feedback.

---

# 5. Architecture design history and alternatives explored

Moved to [docs/history/helix-design-history.md](../history/helix-design-history.md). The master remains the normative source; this section holds no rules.

---

# 6. Final architecture at a glance

```mermaid
flowchart TD
    APP[App / Composition Root] --> SCREEN[Feature Screens]
    APP --> CELL[Feature Cells]
    SCREEN --> CELL
    SCREEN --> UI[Stateless UI]
    CELL --> UI
    SCREEN --> CAPI[Capability APIs]
    CELL --> CAPI
    CAPI --> CIMP[Capability Implementations]
    CIMP --> RES[Snapshot / Live / Projection Resources]
    RES --> DB[SQLDelight / durable state]
    RES --> NET[REST / WebSocket]
    CIMP --> FND[Foundation mechanisms]
    CIMP --> PAPI[Platform APIs]
    APP --> PIMPL[Platform implementations]
    APP --> DB

    CP[Helix Control Plane<br/>CLI + graph + scripts + Skills + verify + doctor] -.enforces/evolves.-> APP
    CP -.-> SCREEN
    CP -.-> CELL
    CP -.-> CAPI
    CP -.-> CIMP
```

The diagram is conceptual. Actual allowed dependencies are specified later.

## 6.1 Application plane and control plane

Helix can be understood as two intertwined systems.

### Application plane

Owns product behavior and runtime state:

- Screens;
- Cells;
- UI;
- Capabilities;
- Resources;
- Foundation/Platform mechanisms;
- persistence;
- navigation;
- DI.

### Control plane

Owns consistency and evolution:

- convention plugins;
- graph rules;
- architecture tests;
- ABI checks;
- generators;
- `helix-kmp` CLI;
- deterministic scripts;
- AI Skills/context packets;
- `impact`/`doctor`;
- refactoring recipes/codemods;
- qualification suites;
- exception registry;
- architecture-kit migrations.

This dual structure is one reason the name **Helix KMP** was selected: application and control/evolution paths are intertwined but remain distinguishable.


---

# 7. Canonical vocabulary

The vocabulary is intentionally small. New architecture nouns require strong justification.

## 7.1 Feature

A **Feature** is a product-facing presentation scope and normally a physical `:feature:*` module.

Examples:

- `:feature:home`
- `:feature:cricket`
- `:feature:article`
- `:feature:subscription`

A Feature may own:

- Screens;
- Cells;
- presentation ViewModels;
- State/Action/UiCommand/Output types;
- presentation mappers;
- local presentation helpers;
- feature fixtures and tests;
- public presentation entry points needed by parent surfaces.

A Feature does not become the owner of shared business/resource state merely because one of its Screens needs it.

## 7.2 Screen

A **Screen** is a navigation destination or other major owner-level presentation surface.

A Screen may:

- own screen-local presentation state;
- compose Cells and pure UI;
- map Outputs to navigation/owner actions;
- own static/dynamic composition policy for its surface;
- host pull-to-refresh or surface-level triggers.

A Screen should not aggregate unrelated domain resource state in one God ViewModel.

Ordinary Screens are called Screens. Do not call everything a "Screen Cell."

## 7.3 Cell

A **Cell** is:

> **An independently hostable stateful presentation unit owned by a Feature.**

Examples:

- `LiveScoreCell`
- `PortfolioCardCell`
- `PromoCarouselCell`
- `FollowedTeamsCell`

A Cell is not:

- a mandatory base class;
- a mandatory interface;
- a Gradle module;
- a Capability;
- a Resource;
- a design-system component;
- a registry descriptor;
- a navigation destination by definition.

There is no required architecture type:

```kotlin
interface Cell<State, Action>
```

A concrete Cell entry may simply be:

```kotlin
@Composable
fun LiveScoreCell(
    matchId: MatchId,
    instanceKey: FeatureInstanceKey,
    onOutput: (LiveScoreOutput) -> Unit,
)
```

### Why Cell exists

Use Cell when a presentation unit needs one or more of:

- independent hosting on multiple surfaces;
- its own presentation state/lifetime;
- reuse without copying parent Screen logic;
- independent testing/fixtures;
- low blast radius for a meaningful stateful widget;
- independent instance identity while sharing business resources.

Do not introduce a Cell for every row/icon/label.

## 7.4 CellSpec

`CellSpec` is optional composition metadata for registry-driven surfaces.

Example:

`CellSpec` lives in the API-safe presentation foundation (`:foundation:presentation`) because registry hosts and independently compiled Feature modules must share the contract.

```kotlin
interface CellSpec {
    val instanceKey: FeatureInstanceKey
}
```

Concrete specs remain Feature-owned and may live in separate Feature modules:

```kotlin
data class LiveScoreCellSpec(
    val matchId: MatchId,
    override val instanceKey: FeatureInstanceKey,
) : CellSpec
```

A registry resolves the renderer by the spec's concrete class, so there is exactly one spec class per Cell type.
Specs should be data classes so a host can diff successive lists.
`instanceKey` must be built with `FeatureInstanceKey.forPlacement` and never `forScreen`, because a registry surface is a host with placements.

`CellSpec` is deliberately **not sealed** (ADR-40): Kotlin sealed direct subtypes must live in the same module/package, which would prevent independently compiled Feature modules from implementing a shared registry contract.

`CellSpec` does not turn Cell into a base-class architecture.

## 7.5 UI

A **UI** unit is stateless reusable rendering.

Typical contract:

```text
immutable model + callbacks -> Composable
```

It may be local to a Feature until independent reuse appears. If extracted physically, it belongs in `:ui:*`.

UI must not own or import:

- ViewModels;
- Koin;
- navigation;
- repositories;
- Capability APIs/implementations;
- Snapshot resource runtime;
- SQLDelight generated types;
- raw networking.

## 7.6 Capability

A **Capability** is a reusable product/business boundary.

Examples:

- Cricket;
- Article;
- Identity;
- Subscription;
- Bookmarks;
- Comments.

It exposes product semantics and hides implementation mechanisms.

A Capability normally has:

```text
:capability:<name>-api
:capability:<name>-impl
```

but code may start local while it has one consumer and be extracted when evidence justifies a stable reusable boundary.

## 7.7 Capability API

Contains:

- stable domain/product models;
- grouped Query interfaces;
- intent-shaped Command contracts;
- rare stable cross-capability contracts.

It does not expose repositories by default.

It must not expose:

- Ktor types;
- Snapshot resource runtime types;
- SQLDelight rows/queries;
- WebSocket frames;
- Compose/ViewModel types;
- implementation classes.

## 7.8 Capability implementation

Owns hidden product/data behavior:

- use-case/command implementations;
- repositories;
- endpoint DTOs;
- mapping;
- Snapshot resource orchestration;
- SQLDelight access;
- REST/WebSocket clients;
- live-resource coordinators;
- refresh/freshness semantics;
- batching/hydration;
- outbox/retry/idempotency where required.

Capability-internal source naming:

- inside `<Name>CapabilityImpl`, raw backends are `<Name>RemoteSource` (one per transport and the
  only class touching the HTTP client) and `<Name>LocalSource` (one per durable store and the only
  class touching SQLDelight); the local source builds its generated database once with
  `LazyDatabase` over the app's `SqlDriverProvider`;
- `<Name>CapabilityImpl` is the only class combining sources and owns DTO/row-to-model mappers;
- names are role-based and platform-neutral: avoid `Store` (Store5 type, Redux/TCA state
  container), `Service` (Android component, microservice), `Storage` (web `localStorage`), `Cache`
  (the table is the source of truth), and Android's `DataSource` formula;
- Android Guide to app architecture mapping: `RemoteDataSource` -> `RemoteSource`,
  `LocalDataSource` -> `LocalSource`, `Repository` -> `Capability` (Queries + Commands).

## 7.9 Query

A **Query** reads/observes product resource state without expressing a business mutation.

Final default is grouped interfaces, for example:

```kotlin
interface CricketQueries {
    fun match(matchId: MatchId): Flow<ResourceObservation<Match>>
    fun liveScore(matchId: MatchId): Flow<ResourceObservation<LiveScore>>
    fun commentary(matchId: MatchId): Flow<ResourceObservation<List<Commentary>>>
}
```

This replaced the earlier default of one trivial class per read such as `ObserveLiveScoreUseCase`.

A dedicated Query/use-case class is still acceptable when behavior is substantial enough to deserve its own named abstraction.

## 7.10 Command

A **Command** expresses business intent that may change state.

Examples:

- `FollowTeam`
- `BookmarkArticle`
- `SubmitVote`
- `PurchaseSubscription`

Commands remain intent-shaped rather than being grouped into generic CRUD.

Avoid names such as:

- `UpdateEntity`
- `SaveData`
- `ExecuteMutation`

unless that is genuinely the product meaning.

## 7.11 Domain Event

A typed **Event** is a fact that already happened and may have multiple independent reactions.

Use only when direct calls would create undesirable fanout/coupling.

Examples:

- `UserLoggedOut`
- `SubscriptionChanged`

Do not create a global string EventBus.

## 7.12 Resource

A **Resource** is shared observable product data with a stable identity and an authoritative owner below presentation.

Helix recognizes three resource types:

1. Snapshot Resource;
2. Live Resource;
3. Projection.

## 7.13 Snapshot Resource

Fetched/synchronized resource whose current state can be observed.

Examples:

- Article;
- match metadata;
- profile;
- weather;
- recommendation page.

Typical current implementation:

```text
Query -> SyncCoordinator.observing(local/SQLDelight) + sync/REST
```

Capability-internal source names follow §7.8.

## 7.14 Live Resource

A shared keyed resource with an ongoing stream/connection lifecycle.

Examples:

- live score;
- market ticker;
- presence;
- live comments.

The resource/connection lifetime is capability-owned, not Cell-owned.

## 7.15 Projection

A derived read over existing resources.

Examples:

```text
cart + promotions -> checkout summary
match + user favorites -> followed-match state
article + bookmark -> article-detail projection
```

Simple projections may use `Flow.combine`. Expensive/frequently rendered joins may justify a named/materialized read projection.

## 7.16 Foundation

Product-agnostic technical mechanisms.

Examples:

- network client construction;
- clocks/time;
- coroutine dispatchers;
- observability contracts;
- generic presentation primitives;
- preferences mechanism.

Foundation must not know Cricket, Article, Subscription, or other product vocabulary.

## 7.17 Platform mechanism / optional Platform API + Impl

OS/device mechanisms. A simple mechanism may be one cohesive KMP module using source sets / `expect`-`actual`; API/Impl pairs are used when implementation selection/isolation earns the physical split.

Examples:

- sharing;
- notifications;
- permissions;
- file picker;
- secure storage;
- camera;
- background work;
- connectivity.

Product meaning remains in Feature/Capability.

## 7.18 Storage

Physical database assembly:

- contributor registration for Capability-owned SQLDelight schemas;
- per-platform driver construction and opening;
- the merged migration sequence;
- assembly-level cross-capability queries/projections where needed.

Storage does not own capability tables or become the product-facing data API. The App composition
root bridges the assembled database to each Capability's narrow database-source interface.

## 7.19 App

The composition root.

Owns:

- platform startup;
- Navigation root;
- DI graph assembly;
- concrete Capability/Platform implementation selection;
- assembled-database binding to Capability implementations;
- global adapter installation.

Nothing depends on App.

## 7.20 Scope

A **scope** is a logical tooling/context grouping such as `cricket`, not a new module role.

Example working set:

```text
scope=cricket
  :feature:cricket
  :capability:cricket-api
  :capability:cricket-impl
  :ui:cricket-score
```

Do not create:

```text
:scope:cricket
:capsule:cricket
```

Do not create aggregator modules that re-export all scope modules.

Scope is derived from naming conventions and rare central overrides.

## 7.21 State

Immutable render state required to reconstruct the current UI.

State shape is product-specific.

## 7.22 Action

Something the user/environment did that the presentation owner handles.

## 7.23 UiCommand

Transient instruction to **this Screen/Cell's own UI**.

Correctness must not depend on delivery.

Good:

```text
FocusSearchField
ScrollToComment(commentId)
ShowSnackbar(messageId)
```

Bad:

```text
ShowComment(comment)    // domain state
UpdateScore(score)      // resource truth
LoadArticle(article)    // correctness-bearing data
```

## 7.24 Output

Semantic message from a child presentation unit to its owner.

Example:

```kotlin
sealed interface LiveScoreOutput {
    data class OpenMatch(val matchId: MatchId) : LiveScoreOutput
}
```

## 7.25 RouteKey

Navigation identity.

Example:

```text
cricket-details/match-123
```

## 7.26 FeatureInstanceKey

Presentation/runtime instance identity.

Examples:

```text
home-feed:live-score:match-123
article-99:related-live-score:match-123
```

## 7.27 ResourceKey

Shared product/resource identity.

Example:

```text
match-123
```

## 7.28 RefreshTrigger / refresh orchestration

Common external opportunity such as:

- foreground;
- reconnect;
- visibility;
- pull-to-refresh;
- periodic trigger.

The trigger does not own product freshness semantics.

## 7.29 BatchHydrator

An internal optimization that may call an aggregate backend endpoint and feed results to capability-owned resource writers.

It is not an observable app-state owner or presentation Query surface.

## 7.30 FeatureObserver

Vendor-neutral operational observability interface for presentation/capability runtime evidence.

## 7.31 ProductAnalytics

Separate typed product/business analytics seam.

Do not conflate operational tracing with business analytics.

## 7.32 ResourceObservation

A transport-neutral observation envelope for synchronized Capability reads when presentation needs
the current durable value plus refresh, failure and confirmed-absence semantics.

It is **not** a universal presentation State and does not expose Snapshot runtime, Ktor or
SQLDelight types. `value = null` with `Idle` means confirmed absent; `initial()` remains
`Refreshing` so absence is never confused with a key that has not synchronized.

Canonical semantics are defined in Section 13.7.

## 7.33 Control plane

The architecture-operating system around the codebase:

- CLI;
- graph;
- generators;
- scripts;
- Skills;
- context;
- verification;
- diagnostics;
- impact;
- codemods;
- qualification;
- architecture-kit evolution.

---

# 8. Physical module taxonomy

## 8.1 Standard roles

```text
:app:*
:feature:*
:capability:*-api
:capability:*-impl
:ui:*
:foundation:*
:platform:*-api
:platform:*-impl
:storage:*
:testkit:*
:tooling:*
:build-logic
```

This is a taxonomy of allowed roles, not a requirement to create every role or an empty module for every concept.

## 8.2 Role responsibilities

### `:app:*`

- composition root;
- navigation/back-stack root;
- DI graph assembly;
- platform startup;
- concrete implementation selection;
- target-specific app shells.

### `:feature:*`

- Screens;
- Cells;
- presentation ViewModels;
- State/Action/UiCommand/Output;
- feature-owned fixtures/tests;
- minimal public presentation entries.

### `:capability:*-api`

- grouped Queries;
- Commands;
- stable product/domain models;
- rare stable cross-capability contracts.

### `:capability:*-impl`

- implementation logic;
- repositories;
- REST/DTO/mapping;
- Snapshot resource orchestration;
- capability-owned SQLDelight schema and access;
- WebSocket/live coordinators;
- batching;
- command/outbox internals.

### `:ui:*`

- stateless reusable rendering;
- immutable UI models;
- callbacks;
- visual resources.

### `:foundation:*`

Generic technical mechanisms with no product vocabulary.

For graph enforcement, every Foundation module has one of two **derived subroles** selected by its convention plugin:

```text
foundation_api
  safe to expose to Feature / UI / Capability API
  product-neutral contracts, IDs, clocks, resource observation, presentation helpers

foundation_runtime
  lower implementation/runtime mechanism
  networking, concurrency/runtime engines, serialization/vendor-facing machinery
```

Physical paths remain `:foundation:*`; this is a validator classification, not a new directory hierarchy.

Examples:

```text
:foundation:presentation     -> foundation_api
:foundation:resource         -> foundation_api
:foundation:resource-runtime -> foundation_runtime
:foundation:sqldelight       -> foundation_runtime
:foundation:time             -> foundation_api
:foundation:network          -> foundation_runtime
:foundation:runtime          -> foundation_runtime
```

### `:platform:*` and optional `:platform:*-api` / `:platform:*-impl`

Simple KMP platform mechanisms may begin as one cohesive `:platform:<name>` module using normal source sets / `expect`-`actual`.

Split into `-api` / `-impl` only when a physical wall reduces expected change size: multiple selectable implementations, heavy/vendor dependencies, independent replacement/testing, or stable consumer API versus volatile implementation.

The responsibility remains OS/device mechanisms either way.

### `:storage:*`

- contributor schema assembly;
- per-platform drivers and database opening;
- merged migration sequencing;
- assembly-level cross-capability joins/projections.

### `:testkit:*`

- shared test primitives/fakes/helpers;
- not a place for product-specific scenarios that belong near the owning Feature/Capability.

### `:tooling:*`

- Helix CLI;
- graph;
- context/impact/doctor;
- generators;
- recipes;
- qualification harness;
- gallery tooling.

### `:build-logic`

Included build containing role convention plugins and graph/verification tasks.

## 8.3 Start cohesive

Do not create a pre-emptive matrix such as:

```text
:feature:x-api
:feature:x-impl
:cell:y-api
:cell:y-impl
:ui:x-impl
```

for every concept.

Create physical isolation when it has measurable value.

Capability clarification:

- one-consumer business/data behavior may remain local while genuinely local;
- when promoted to a reusable cross-module Capability, the default extracted shape is `:capability:<name>-api` + `:capability:<name>-impl`;
- revisit that split if measured KMP build/IDE cost proves it does not reduce expected change size.

Platform clarification:

- simple platform mechanism may remain one `:platform:<name>` module;
- `-api` / `-impl` is an extraction/selection tool, not mandatory ceremony.

---

# 9. Dependency direction

## 9.0 Canonical machine-readable dependency policy

The role-to-role dependency matrix is [`config/helix/dependency-policy.json`](../../config/helix/dependency-policy.json). That file is the single authority for role-to-role dependency validation: `checkModuleGraph` and the `helix-kmp` CLI read it directly, and no copy of it lives in this master. The sections below explain what it says; they are rendered explanations, not a second source.

Policy semantics:

- `defaultDecision = deny` means every role pair not explicitly allowed is rejected;
- no second `deny` list is needed;
- `foundation_api` and `foundation_runtime` are convention-plugin classifications of physical `:foundation:*` modules;
- the Feature -> Feature exception is machine-checkable:
  - the target Feature's externally reusable presentation surface must be public only under its `.api` package;
  - source analysis must prove the caller imports only that target `.api` package;
  - any import from the target Feature outside `.api` fails `DEP-FEATURE-FEATURE-PUBLIC-PRESENTATION-ONLY`;
- Feature convention/source checks must reject public Feature symbols outside the approved `.api` surface except generated/platform-required artifacts.

Stable rule identifiers appear in CLI/CI failures and `doctor` output.

That policy governs **main/application dependency edges**. Test source sets may depend on `:testkit:*` through test configurations, and `:tooling:*` / `:build-logic` are validated as build/control-plane code rather than as runtime application dependencies.

## 9.1 Allowed matrix

| From | May depend on | Must not depend on |
|---|---|---|
| App | Feature, UI, Capability API/Impl, Foundation API/Runtime, Platform API/Impl, Storage | nothing may depend on App |
| Feature | UI, Capability API, `foundation_api`, Platform API/cohesive Platform; child Feature only through approved `.api` public presentation surface | Capability Impl, `foundation_runtime`, raw repository/data implementation, peer Feature internals |
| UI | UI/design + `foundation_api` | Feature, ViewModel, Koin, navigation, Capability, `foundation_runtime`, repository, DB/network |
| Capability API | other Capability APIs where semantically real + `foundation_api` | Compose, Feature, UI, `foundation_runtime`, network, DB, resource runtime, implementation |
| Capability Impl | own/other Capability APIs, `foundation_api`, `foundation_runtime`, Platform APIs/cohesive Platform | Feature, UI, Storage, other business Capability Impl |
| Foundation API | `foundation_api` | product Feature/Capability vocabulary; `foundation_runtime` |
| Foundation Runtime | `foundation_api`, `foundation_runtime` | product Feature/Capability vocabulary |
| Platform | `foundation_api`, `foundation_runtime`, platform SDK/source sets | product Feature/UI internals |
| Platform API | `foundation_api` | product Feature/UI internals; implementation/vendor details |
| Platform Impl | own Platform API, `foundation_api`, `foundation_runtime`, platform SDK | product Feature/UI internals |
| Storage | Capability Impl schema contributors, `foundation_api`, `foundation_runtime`, DB/tooling mechanisms | product presentation |
| Anything | - | App |

## 9.2 Key prohibitions

These are high-value rules and should fail mechanically.

```text
Feature -> Capability Impl                       FORBIDDEN
Feature -> raw repository/DB/network impl       FORBIDDEN
UI -> Feature/ViewModel/Koin/navigation         FORBIDDEN
UI -> Capability/repository/resource runtime/SQLDelight FORBIDDEN
Capability API -> Compose/ViewModel             FORBIDDEN
Capability API -> network/DB/resource runtime/impl      FORBIDDEN
Capability Impl -> Feature/UI                   FORBIDDEN
Capability Impl -> another business Impl        FORBIDDEN
Anything -> App                                 FORBIDDEN
Peer Cell impl -> peer Cell impl                FORBIDDEN
```

## 9.3 Hierarchical Feature composition

Hierarchical presentation composition is allowed when a parent actually renders a child's **public presentation entry**.

Example:

```text
HomeScreen -> Cricket LiveScore public Cell entry      OK
LiveScoreCell -> another Cell implementation           NOT OK
LiveScoreCell -> Subscription Capability API           OK
```

Rules:

- an externally reusable child presentation entry is placed in the child Feature's approved `.api` package;
- public symbols outside that Feature `.api` surface fail source/ABI policy;
- parent may import only the child Feature's `.api` package;
- child does not import parent;
- child ViewModel/data internals stay internal;
- shared business/resource facts go through Capabilities;
- do not use Feature dependency merely to obtain business logic.

The graph conditional allow and source-import check together make this exception decidable rather than prose-only.

## 9.4 Physical and logical cycle checks

Check both:

1. physical Gradle cycles;
2. logical business cycles after normalizing `foo-api + foo-impl` into one Capability family.

A physical API/Impl split can otherwise hide a semantic cycle.

## 9.5 Approved cycle remedies

When a cycle appears, use one of:

- extract shared Capability behavior;
- extract shared stateless UI;
- lift coordination to the nearest common owner;
- merge modules that are not truly independent.

Do not hide cycles behind:

- service locators;
- global EventBus;
- arbitrary interface-only modules created solely to fool Gradle;
- implementation-to-implementation dependencies.

## 9.6 Standard Gradle syntax

Use ordinary Gradle project dependencies:

```kotlin
dependencies {
    implementation(project(":capability:cricket-api"))
}
```

Architecture meaning is derived from role/path/graph.

Do not introduce a custom dependency DSL such as:

```kotlin
architecture {
    dependsOnCapability("cricket")
}
```

unless future evidence proves Gradle cannot express a required fact.

## 9.7 No per-module architecture YAML

Do not require each module to author files like:

```text
module.yaml
scope.yaml
architecture.yaml
```

for facts already derivable from build/source/ownership data.

Rare exceptional mappings belong in one reviewed central override registry.

## 9.8 Public API and ABI

- keep implementation types internal;
- use explicit API mode for stable public API modules where useful;
- use Kotlin ABI validation for Capability APIs/shared UI libraries;
- public-surface widening should be an intentional reviewed diff.

## 9.9 Phase-two target visibility

Role rules answer which *kind* of module may depend on another kind.

If real accidental coupling later appears, add target-specific visibility, for example:

```text
:ui:cricket-internal visible only to scope=cricket
```

Do not add this complexity before evidence requires it.

---

# 10. Reuse and extraction model

## 10.1 Reuse decision tree

```text
Need exact rendering only?
  -> keep local or extract :ui:*

Need reusable business/data semantics?
  -> Capability API/Impl

Need the complete autonomous stateful widget?
  -> reuse Feature-owned public Cell entry

Need only one consumer today?
  -> keep cohesive/local until extraction evidence appears
```

## 10.2 UI extraction

Move only:

- immutable rendering model;
- stateless Composable;
- shared visual resources.

Do not move:

- ViewModel;
- navigation;
- Koin;
- Capability calls;
- repository/data implementation.

## 10.3 Capability extraction

Trigger: a second independent consumer needs business/data behavior, or other evidence says a stable physical boundary reduces expected change size.

Move:

- stable product models;
- grouped Query/Command contracts;
- hidden implementation/repository/data.

## 10.4 Module extraction is objective, not stylistic

Possible `doctor` signals:

- independent consumers;
- second owner team;
- context budget breach;
- public API/ABI fanout;
- compile/test fanout;
- dependency fanout;
- repeated illegal-edge pressure;
- low co-change between pieces;
- volatile implementation behind stable API.

## 10.5 Merge over-fragmented boundaries

If modules:

- always change together;
- share one owner;
- have no independent consumer;
- add build/reading overhead;

then merging may improve architecture.

"More modular" is not synonymous with "more modules."


---

# 11. Presentation architecture

## 11.1 Choose the lightest presentation shape

Helix does not require one universal runtime profile.

### Profile A - Simple content

Use for ordinary feed/list/content rows with no autonomous state/lifetime.

```text
immutable model -> Composable
```

No ViewModel, DI owner, Cell identity, registry, or module is required merely because the item is visible on screen.

### Profile B - Cell

Use when embedded presentation needs independent state/hosting/lifetime/reuse.

```text
Cell entry
  -> ViewModel
  -> State/Action
  -> Capability APIs
```

### Profile C - Screen

Use for navigable destination ownership.

```text
RouteKey -> NavEntry -> Screen ViewModel -> presentation
```

A Screen may compose Cells.

### Profile D - Complex workflow

A complex editor/checkout/media/workflow may use an internal reducer or state machine when that genuinely clarifies correctness.

Helix does not impose a global reducer/store runtime on all presentation.

## 11.2 Cell structure is complexity-driven

Simple Cell:

```text
WeatherCell.kt
WeatherCellTest.kt
```

Larger Cell:

```text
:feature:cricket/
  live_score/
    LiveScoreCell.kt
    LiveScoreViewModel.kt
    LiveScoreState.kt
    LiveScoreFixtures.kt
    LiveScoreViewModelTest.kt
    LiveScoreUiTest.kt
```

Possible generator command:

```bash
helix-kmp create cell cricket live-score
```

The generator does not force seven files. It selects/creates the minimum standard structure required by the requested profile.

For a stateful Cell, the generator defaults to **State + Action only**. `UiCommand` and `Output` are added only when the caller explicitly requests those concepts (for example through generator options) or the generated acceptance shape demonstrates they are required. The generator should not create empty event types merely for symmetry.

Illustrative public intent:

```bash
helix-kmp create cell cricket live-score
helix-kmp create cell cricket live-score --with-output
helix-kmp create cell search results --with-ui-command
```

The exact option spelling is a CLI-version detail; the architectural rule is that optional concepts are **opt-in rather than boilerplate**.

## 11.3 UDF ViewModel convention

Stateful Screens/Cells use AndroidX ViewModel as the default state/coroutine owner, with a minimal unidirectional vocabulary.

Default:

```text
State   mandatory
Action  mandatory
```

Optional:

```text
UiCommand
Output
```

Do not require a generic base class such as:

```kotlin
FeatureViewModel<State, Action, UiCommand, Output>
```

The convention is vocabulary/behavior, not inheritance.

## 11.4 State

State is immutable render truth for the presentation owner.

Example:

```kotlin
data class LiveScoreState(
    val title: String,
    val homeScore: String,
    val awayScore: String,
    val isRefreshing: Boolean,
    val isOffline: Boolean,
)
```

### No universal state machine

Do not require:

```text
Loading | Content | Error | Hidden
```

for every Cell.

A generator may scaffold common Loading/Content/Error examples when useful, but they are not an interface.

Rendering nothing is legal when product semantics say no UI should exist, for example "no live match." That does not require a universal `Hidden` state.

## 11.5 Action

Action represents user/environment input to the owner.

Example:

```kotlin
sealed interface LiveScoreAction {
    data object Retry : LiveScoreAction
    data object Refresh : LiveScoreAction
    data object OpenMatch : LiveScoreAction
}
```

UI interactions should normally enter the ViewModel/owner first so it can apply:

- access/auth checks;
- product/business guards;
- analytics;
- state transition;
- command invocation.

Do not bypass the owner merely because the final action is navigation.

## 11.6 UiCommand

UiCommand is optional and local/transient.

Rule:

> **Correctness must not depend on UiCommand delivery.**

A lint/Konsist-style fence should prohibit correctness-bearing domain/resource payloads where practical.

Allowed payloads:

- primitive values;
- IDs;
- presentation targeting values.

Examples:

```text
ScrollToComment(commentId)     OK
ShowComment(comment)           NOT OK
FocusSearchField               OK
SetCurrentArticle(article)     NOT OK
```

Historical evolution: earlier drafts used generic `Effect`; final terminology is `UiCommand` because it better communicates the narrow local-UI purpose.

## 11.7 Output

Output is optional semantic communication to the owning presentation layer.

Example:

```kotlin
sealed interface LiveScoreOutput {
    data class OpenMatch(val matchId: MatchId) : LiveScoreOutput
}
```

Output is not a general peer-to-peer event channel.

## 11.8 Classification rule

```text
Must UI reconstruct it?                 -> State
Did the user/environment do it?         -> Action
Transient instruction to my own UI?     -> UiCommand
Semantic message to my owner?           -> Output
```

## 11.9 Prefer derived StateFlow

Resource truth should normally flow into presentation rather than be mirrored into mutable copies.

Prefer:

```text
resource Flow
  -> map/combine
  -> stateIn
  -> StateFlow<State>
```

Use `MutableStateFlow` for genuinely local presentation state such as:

- selected local tab;
- draft form input;
- expansion state;
- local filtering/sorting options.

Do not copy shared resource truth into multiple independently mutable ViewModel fields.

## 11.10 Feature public presentation boundary

Feature implementation types remain internal.

A public presentation entry should expose only what a parent needs to host it:

- stable parameters/IDs;
- callbacks/Outputs;
- Composable entry or facade.

Consumers do not obtain another Feature's ViewModel directly.

## 11.11 Kill switches and flags

If a product flag determines whether a Cell exists, exclude it at the composition site **before instantiation** where practical.

Registry surface:

```text
flag/policy -> CellSpec list -> registry -> instantiate Cell
```

Static surface:

Use a conventionized guard location generated by the standard template rather than scattered ad-hoc `if (flags...)` checks throughout Cell internals.

The Cell should not need to know that a global kill switch exists merely to render itself.

## 11.12 Composition registry decision rule

Ask one mechanical question:

> **Can product independently change which Cells exist on this surface or their ordering/variant?**

### YES -> registry from day one

Typical surfaces:

- Home;
- Discover;
- Dashboard;
- experiment-heavy feed;
- personalization surfaces;
- remote kill-switch/dynamic insertion surfaces;
- likely SDUI evolution points.

### NO -> normal Compose hierarchy

Typical surfaces:

- Login;
- Settings;
- Article detail;
- Checkout;
- Video player;

unless requirements change.

If repeated retrofits show the classification is wrong, revisit the default.

### 11.12.1 Registry dependency ownership

Static hierarchical composition may directly depend on a child's minimal public presentation entry.

For a registry-driven surface, the host Feature must not become the compile-time transitive root of every registered Feature.

```text
Feature modules provide registrations/renderers
        -> App composition root populates generic Cell registry
        -> dynamic host depends only on generic registry/CellSpec contract
        -> registry resolves the concrete Feature entry
```

Rules:

- generic registry/CellSpec hosting contract lives in a small presentation-neutral mechanism such as `:foundation:presentation`;
- concrete renderer/parameter handling remains owned by the Feature;
- App/assembly installs registrations;
- dynamic Home/Discover does not import every registered Feature;
- do not introduce this indirection into static screens.

## 11.13 No-sideways presentation

Peer presentation units do not depend on one another's internals.

```text
HomeScreen -> Cricket public Cell entry       OK
CricketLiveScore -> Subscription API          OK
CricketLiveScore -> PromoCell implementation  NOT OK
```

When two Cells appear coupled, ask whether the shared fact belongs in:

- a Capability Resource;
- a Projection;
- a parent owner/composition decision;
- an explicit domain Event in the rare many-to-many case.

## 11.14 Fixtures are architecture, not decoration

Meaningful/stateful Cells provide fixtures for relevant states.

Examples:

```text
loading
content
empty
offline
error
long text
large font
dark mode
compact
expanded
stale/live
subscription-required
```

Do not force irrelevant fixture states.

Fixtures should support:

- Compose preview;
- screenshot/golden tests;
- accessibility review;
- product/design review;
- QA reproduction;
- AI-agent visual validation.

## 11.15 Fixture gallery

A debug/review surface enumerates meaningful Cells and fixture states without a live backend.

Possible CLI:

```bash
helix-kmp gallery
```

This is particularly important for non-mobile/non-developer review.

## 11.16 Presentation ceremony metrics

Because generated structure still has reading cost, monitor real Cells. During adoption, use the **first roughly 10 real production Cells** as the initial falsification sample; after that, continue metrics where they remain useful.

Useful metrics:

- file count per Cell;
- architecture/scaffolding LOC vs product logic LOC;
- common files always opened together;
- repeated boilerplate hidden by generator but costly to read;
- agent context tokens per normal Cell change;
- time to locate state/resource owner;
- number of exceptions/workarounds.

If the convention repeatedly produces more structure than value, simplify it.

---

# 12. Runtime ownership and identity

## 12.1 The ownership questions

For every stateful/runtime object, be able to answer:

1. What is its identity?
2. Who creates it?
3. Who owns it?
4. How long should it live?
5. What event destroys/releases it?
6. How does it recover after process/application restart if required?

If these answers are vague, the runtime ownership is not finished.

## 12.2 Navigation ownership

Current selected runtime: Navigation 3.

```text
RouteKey
  -> NavEntry
      -> entry ViewModelStoreOwner
          -> Screen ViewModel
```

When the entry is removed from the back stack, its Screen ViewModel must be destroyed.

Feature code emits semantic navigation Outputs/callbacks; app/navigation code owns URL/history mapping.

## 12.3 Typed routes

Example:

```kotlin
sealed interface RouteKey

data object HomeRoute : RouteKey

data class MatchRoute(
    val matchId: MatchId,
) : RouteKey
```

URL-visible state that must survive direct links/browser refresh belongs in route contracts, not only inside a ViewModel.

## 12.4 Smart embedded Cell ownership

Embedded Cells in lazy containers need a lifetime between raw composition and navigation entries.

Target behavior:

```text
logical Cell exists and is visible
  -> composition exists
  -> keyed owner exists

Cell scrolls offscreen
  -> composition may be disposed
  -> keyed owner/state retained

Cell returns
  -> same FeatureInstanceKey
  -> same presentation state

Cell removed from logical collection
  -> owner clear requested immediately
  -> ViewModel cleared when no composed item holds the owner
```

## 12.5 `StatefulLazyItem`

`StatefulLazyItem` is the working name for one narrow shared helper that owns keyed embedded ViewModel lifecycle.

It is infrastructure, not a general component framework.

It may provide:

- keyed `ViewModelStoreOwner`;
- lifecycle owner/capping if required;
- saved-state bridge where needed;
- deterministic cleanup on logical removal;
- diagnostic FeatureInstanceKey.

When hosting Cells inside a lazy list, the host derives `activeKeys` from the viewport with
`rememberViewportKeys`, including its bounded prefetch buffer. Refresh gating remains a ViewModel
concern: use `stateIn(SharingStarted.WhileSubscribed(...))` with periodic refresh inside the shared
flow so work stops when the item leaves composition and resumes when it returns.

It should not grow into:

- nested navigation runtime;
- back-dispatch tree;
- arbitrary retained component hierarchy;
- custom state keeper ecosystem;
- general child runtime framework.

The helper delegates keyed store ownership and ref-counted retirement to lifecycle 2.11.0
`ViewModelStoreProvider`. Reconsider Decompose if it grows beyond this narrow scope or recurring
lifecycle bugs show that a component runtime is warranted.

## 12.6 Required keyed-owner regression suite

At minimum:

1. compose item;
2. mutate local presentation state;
3. remove from composition due to scroll/recycling while logical item remains;
4. return to composition;
5. verify state remains;
6. remove item from logical dataset;
7. verify owner/ViewModel clears immediately when uncomposed, or after disposal when removal races
   an active composition reference;
8. re-add as new logical instance;
9. verify old state does not leak.

## 12.7 Three independent identities

```text
ResourceKey
  = business/shared resource identity
  = match-123

FeatureInstanceKey
  = local presentation/runtime identity
  = home-feed/live-score/slot-987

RouteKey
  = navigation identity
  = cricket-details/match-123
```

Example:

```text
Cell A
  FeatureInstanceKey = home-feed/live-score/slot-12
  ResourceKey        = match-123
  expanded           = false

Cell B
  FeatureInstanceKey = article-99/live-score/related-slot-1
  ResourceKey        = match-123
  expanded           = true
```

Result:

- independent presentation state;
- shared business resource;
- one live-resource connection where policy allows.

## 12.8 FeatureInstanceKey construction contract

**Module home:** presentation identity primitives (`FeatureInstanceKey`, `CellPlacementId`) live in `:foundation:presentation`, classified as `foundation_api`.

`FeatureInstanceKey` is not an arbitrary string convention.

Every segment after the leading surface/route is validated by one helper:

```kotlin
private fun requireSegment(label: String, value: String) {
    require(value.isNotBlank()) { "$label cannot be blank" }
    require('/' !in value) { "$label cannot contain '/'" }
}
```

The host owns a distinct placement identifier:

```kotlin
@JvmInline
value class CellPlacementId private constructor(
    val value: String,
) {
    companion object {
        fun fromHostStableId(value: String): CellPlacementId {
            requireSegment("A Cell placement ID", value)
            return CellPlacementId(value)
        }
    }
}
```

Feed/registry models carry placement identity separately from business resource identity:

```kotlin
data class CricketPlacement(
    val placementId: CellPlacementId,
    val matchId: MatchId,
)
```

`FeatureInstanceKey` combines surface + Cell type + placement:

```kotlin
@JvmInline
value class FeatureInstanceKey private constructor(val value: String) {
    companion object {
        fun forPlacement(
            surface: String,
            cellType: String,
            placement: CellPlacementId,
        ): FeatureInstanceKey {
            require(surface.isNotBlank())
            requireSegment("A Cell type", cellType)
            return FeatureInstanceKey("$surface/$cellType/${placement.value}")
        }
    }
}
```

Rules:

- placement identity remains stable across reorder/recomposition while the logical placement exists;
- two instances of the same Cell type on one surface require different `CellPlacementId`s;
- a `MatchId`, `ArticleId`, `ResourceKey`, list index, or other domain/resource identifier cannot be passed directly because the factory requires `CellPlacementId`;
- hosts derive `CellPlacementId` from their own stable placement/slot identity, not from the ResourceKey;
- the Cell type and the placement must not contain `/`; the leading surface or route is the prefix and may. Both factories enforce it, and `CellPlacementId` enforces it for placements;
- construct the `FeatureInstanceKey` once in the host and pass/reuse that exact value;
- debug registry/lazy hosts **must detect duplicate active `FeatureInstanceKey`s** within one owner/surface and fail loudly in development/test builds.

The `/` delimiter is fixed and segments may not contain it; the typed placement semantics are normative.

## 12.9 State survival ladder

### Durable/resource state

Owner: Capability/SQLDelight/secure storage/outbox as appropriate.

Survives process death according to product retention policy.

Examples:

- article cache;
- match snapshot;
- bookmarks;
- pending durable command.

### Presentation-instance state

Owner: Screen/Cell ViewModel keyed by route/FeatureInstanceKey.

Survives composition churn/configuration according to runtime owner.

Examples:

- expanded section;
- selected local tab;
- unsaved UI mode.

### Saveable scraps

Small state that must restore after owner recreation and is appropriate for save-state mechanisms.

Do not use saved state as a duplicate domain/resource store.

### Composition-only state

Transient render/animation/layout state that can disappear with composition.

## 12.10 Feature lifetime is not resource lifetime

A Cell leaving composition or a Screen navigating away does not automatically mean a shared resource should be destroyed.

Resource lifetime belongs to the Resource/Capability policy.

## 12.11 Configuration changes and process death

Principle:

- presentation state restores according to owner semantics;
- shared/durable product data restores from resource/durable storage;
- the UI should not rely on a Screen ViewModel as the durability mechanism.

## 12.12 Adaptive layouts

Responsive rearrangement belongs in Compose.

Navigation/presentation ownership should not depend on passing `isTablet`/screen width into ViewModels unless product behavior, not merely layout, changes.

Navigation 3 Scenes/adaptive libraries may render multiple entries/panes while keeping typed route ownership.

## 12.13 Web/browser behavior

For Web/Wasm, permanent qualification must cover:

- browser Back;
- browser Forward;
- URL synchronization;
- direct URL entry;
- page refresh;
- deep links.

Browser-history integration is isolated in app/navigation infrastructure, not Feature code.

## 12.14 Coroutine scope ownership for shared runtime/resource work

Law 1 applies to coroutine scopes.

```text
App composition/root
  owns application runtime parent scope
      -> Capability implementation creates named child jobs/scopes
          -> live coordinators / refresh / outbox / transfer jobs
```

Rules:

- App creates the parent runtime scope through Foundation/runtime infrastructure.
- Use a `SupervisorJob`-style failure boundary, injected dispatcher/context, and central uncaught-error reporting.
- App cancels it on application/runtime shutdown where supported.
- Capability resource owners own/cancel their child jobs according to Capability lifetime.
- live/resource work may outlive ViewModel/composition when subscriber/grace/background policy says the Resource still lives.
- never pass `viewModelScope` into a shared Capability Resource/coordinator.
- never use `GlobalScope` as Helix ownership.
- Feature/business code should not hard-code `Dispatchers.*` where injected dispatcher policy applies.
- platform background scheduling is separate from this in-process scope.

Illustrative mechanism:

```kotlin
class ApplicationRuntimeScope(
    dispatcher: CoroutineDispatcher,
    exceptionHandler: CoroutineExceptionHandler,
) : AutoCloseable {
    private val job = SupervisorJob()
    val scope = CoroutineScope(job + dispatcher + exceptionHandler)
    override fun close() = job.cancel()
}
```

Exact wrapper names may differ; ownership/cancellation semantics are normative.

---

# 13. Capability API and business model

## 13.1 Product language above mechanism

Presentation depends on Capabilities, not repositories/cache/network primitives.

Example:

```kotlin
interface CricketQueries {
    fun liveScore(matchId: MatchId): Flow<ResourceObservation<LiveScore>>
    fun match(matchId: MatchId): Flow<ResourceObservation<Match>>
    fun commentary(matchId: MatchId): Flow<ResourceObservation<List<Commentary>>>
}
```

Commands:

```kotlin
fun interface FollowTeam {
    suspend operator fun invoke(teamId: TeamId)
}
```

## 13.2 Asymmetric lightweight CQRS

Helix uses a CQRS-inspired vocabulary without separate buses/databases.

```text
Query   -> grouped observable reads
Command -> intent-oriented writes/business actions
Event   -> rare fact broadcast when many-to-many decoupling is real
```

The asymmetry is intentional: trivial reads do not need one class each, but business mutations benefit from explicit intent names.

## 13.3 Repositories are internal by default

A repository is often too implementation-oriented and too broad to be the stable cross-module product API.

Expose a repository contract only when it is genuinely the smallest stable product contract across multiple consumers.

## 13.4 Anti-corruption adapters

When integrating an existing/third-party subsystem, adapt it inside Capability implementation.

Do not leak:

- vendor DTOs;
- database rows;
- SDK models;
- transport-specific nullability/field layout;
- cache request/response types.

## 13.5 Cross-capability dependencies

A Capability implementation may depend on another Capability's API if business semantics genuinely require it.

Do not depend on another Capability implementation.

Watch logical cycles.

## 13.6 Query/Command path examples

### Query

```text
LiveScoreViewModel
  -> CricketQueries.liveScore(match-123)
  -> Cricket capability impl
  -> shared resource
  -> StateFlow derivation
```

### Command

```text
Action.FollowTeam
  -> ViewModel
  -> FollowTeam(team-7)
  -> command implementation
  -> API/local transaction
  -> update/invalidate affected resources
```

## 13.7 Resource observation, status and sync contract

**Module home:** `:foundation:resource`, classified as `foundation_api`.

Capability API modules depend on this one product-neutral contract; they must not redefine local copies.

The database is the value owner. A Capability implementation observes its durable rows through a
cold flow and lets `SyncCoordinator<Key>` from `:foundation:resource-runtime` (`foundation_runtime`)
coordinate the work that writes them: one worker runs per key, callers join an in-flight worker
instead of starting another, `syncIfDue` skips a key attempted within `minInterval`, observers are
counted around the upstream flow so the first collection starts one due background sync, every
emission of the constructor's `retryTriggers` flow restarts only observed keys whose last attempt
failed `OFFLINE` (connectivity returning is not evidence that a synchronised value changed), and
`status(key)` exposes the per-key ledger as `SyncStatus`. The trigger flow is platform blind; a
Capability passes its `ConnectivityMonitor.reconnects()`.
The ledger is process-local and bounded: once it exceeds `maxEntries`, the oldest unobserved idle
key is evicted, which resets its status, so consumers collect `status` inside `observing` for the
same key. The coordinator never reads, caches, or compares values, has no grace timeout, and does
not catch failures of the durable flow: a durable read that throws is a defect to surface, not a
resource state.

A plain `Flow<T>` is insufficient for a remotely synchronized read when presentation must distinguish initial load, loaded content, refresh-in-flight, failure without a value, and failure with a retained value.

Canonical contract:

```kotlin
sealed interface Outcome<out T> {
    data class Completed<T>(val value: T) : Outcome<T>
    data class Failed(val problem: Problem) : Outcome<Nothing>
}

data class Problem(val kind: ProblemKind, val reference: String? = null) {
    /** A retry without changing anything may succeed. */
    val retryable: Boolean

    /** The request may have reached the server; a command's effect is unknown. */
    val mayHaveApplied: Boolean
}

enum class ProblemKind { OFFLINE, TIMEOUT, SERVER, FORBIDDEN, UNEXPECTED }

/** One rejected input, with display-ready server text when the server supplied it. */
data class Violation<out F>(val field: F?, val code: String, val message: String? = null)

data class ResourceObservation<T : Any>(
    val value: T?,
    val operation: ResourceOperation,
) {
    companion object {
        fun <T : Any> initial(): ResourceObservation<T> =
            ResourceObservation(value = null, operation = ResourceOperation.Unsynchronized)
    }
}

sealed interface ResourceOperation {
    /** No synchronization has completed in this process yet. */
    data object Unsynchronized : ResourceOperation
    data object Idle : ResourceOperation
    data object Refreshing : ResourceOperation

    data class Failed(
        val problem: Problem,
    ) : ResourceOperation
}

data class SyncStatus(
    val inFlight: Boolean,
    val lastFailure: Problem?,
    val hasSucceeded: Boolean,
)
```

`T : Any` is deliberate. The outer nullable value means the durable source currently has no row.
The operation distinguishes a never-synchronized key (`Unsynchronized`), an attempt in flight
(`Refreshing`), a failed attempt (`Failed`), and a confirmed absence (`Idle`). `isInitialLoading`
is the derived question presentation actually asks: no value yet, and the resource may still
produce one.

**Value semantics:** `value` is what the durable source currently holds for the key, or `null`
while the Capability cannot yet vouch for it. A Capability that needs "this collection was
synchronized at least once" persists that marker next to the rows and writes it in the same
transaction as the rows, so an empty synchronized collection is a legal `Idle` value and a
never-synchronized one stays `null`. The contract carries no age or freshness policy; a Capability
that needs one models the timestamp inside `T`.

**Status mapping:** `SyncStatus.toOperation()` in `:foundation:resource` is the one mapping from
the key's `SyncStatus` to an operation, in this order: `Refreshing` while `inFlight`;
`Failed(lastFailure)` when the last attempt failed; `Unsynchronized` until one has succeeded;
otherwise `Idle`. The durable value takes no part in it.
`SyncCoordinator.observations(key, values)` in
`:foundation:resource-runtime` applies it: it combines the durable value flow with `status(key)`,
drops unchanged emissions and wraps the result in `observing` for the same key, so a Capability
hands over its value flow and never repeats the mapping. A confirmed detail 404 is an endpoint
answer: the implementation removes the local row and completes the sync. The resulting
`ResourceObservation(value = null, operation = Idle)` is confirmed absent, exposed by `isAbsent`.
A clean ledger stays `Unsynchronized` until an attempt succeeds, so a durable value written by an
earlier process is shown as is rather than announced as fresh. A Capability builds one
`CommandBridge(logger, "<capability>")` and routes every command through it, so its sync function
reads `bridge.commit(remoteResult, operation) { persist(it) }` and its logged operations are
prefixed with the capability's tag. Its local source observes SQLDelight through
`:foundation:sqldelight`'s `observeList`, `observeOneOrNull` and `observeOne` helpers, and builds its generated database once with `LazyDatabase` over the app's shared
`SqlDriverProvider`, observing queries through `LazyDatabase.observe`. The app shares its cold platform connectivity monitor once with
`ConnectivityMonitor.shared(applicationScope)`, so every coordinator takes
`connectivityMonitor.reconnects()` directly.

**Command results:**

- `Failed` means the system could not reach a decision (offline, timeout, server failure, no
  access, or a bug such as an undecodable body or an unmapped status). `Completed` means it did,
  and the decision may be a refusal. Every command returns `Outcome<T>`.
- Refusals are modelled in `T` as the command's own sealed result type, for example
  `Outcome<CreateTodoResult>` with `CreateTodoResult = Created(id) | InvalidInput(violations)`.
  Commands with nothing to say on success return `Outcome<Unit>`. There is no generic rejection
  type parameter, no marker interface, no nullable `Problem?`, and no `Failed` case inside domain
  result types.
- The capability implementation decides, per endpoint, which HTTP statuses are answers and maps
  them into `T` before the bridge runs; everything unmapped becomes a `Problem`.
- `Problem` carries no message text. Display-ready server text belongs on refusals (`Violation.message`
  or a field on the refusal case). Features map `ProblemKind` to their own strings.
- `Problem.reference` is the request id (`X-Request-Id`) when one exists, for support and logs.
- Exactly one structured log line per `Failed` classification, none for refusals or success.
- kotlin-result stays inside implementations (`Result<T, NetworkFailure>` from `tryCatching`); the
  capability API and features never see it. Exceptions remain for bugs and cancellation only.

The `NetworkFailure` to `Problem` mapping is fixed: offline and timeout transport failures become
`OFFLINE` and `TIMEOUT`; HTTP 401 or 403 becomes `FORBIDDEN`; HTTP 408, 429 and 5xx become `SERVER`;
any other unmapped HTTP status, decoding failure or unexpected failure becomes `UNEXPECTED`.
`Problem.reference` is the failure's request id. `UNEXPECTED` logs at error severity and every
other kind at warning; the log record itself is owned by `CommandBridge` in
`:foundation:resource-runtime` and pinned by `CommandBridgeTest`. A defect the runtime catches
itself goes through the same bridge: `SyncCoordinator` reports a sync worker that threw through
`CommandBridge.unexpected` at error severity. A worker abandoned because its scope ended has
nothing to diagnose and is not logged.

**RefreshQos semantics:** `CRITICAL_VISIBLE` means the user is blocked on the resource; `VISIBLE`
means the user is looking at it; `BACKGROUND` is maintenance/reconnect work that must not compete
with visible work; and `PREFETCH` is speculative and may be dropped. `ANY_NETWORK` permits any
available network; `UNMETERED_PREFERRED` may fall back to metered; and `UNMETERED_ONLY` skips on a
metered network.

`SyncCoordinator` passes QoS to `sync`. First-observer work and the offline retry on reconnect use
`RefreshQos.background()`. No scheduler exists yet, so every class executes immediately.

The legal structural combinations are exhaustive:

| value | operation | meaning/example |
|---|---|---|
| `null` | `Unsynchronized` | nothing durable and nothing synchronized yet in this process |
| `null` | `Refreshing` | initial load: a sync is in flight and there is still nothing to show |
| `null` | `Failed(...)` | failure with no value to show |
| `null` | `Idle` | synchronization confirmed that the detail is absent |
| `T` | `Unsynchronized` | durable value from an earlier process, shown before any sync succeeded |
| `T` | `Idle` | durable value, no active sync |
| `T` | `Refreshing` | durable value shown while a sync runs |
| `T` | `Failed(...)` | durable value retained after a failed sync, typically offline |

Rules:

- coordinator/Ktor/SQLDelight exceptions and implementation types never leak through this contract.
- `ResourceObservation` is not a universal UI State; ViewModels map it to product-specific State.
- product-specific failure semantics may add stable domain contracts; raw HTTP/errors do not escape.
- session expiration is primarily an Identity/session transition; `FORBIDDEN` may describe the read attempt while that transition resolves.
- purely local reads with no synchronization semantics may remain `Flow<T>`.
- Projections derive observation status intentionally instead of discarding failure information.
- Live Resources may use the same envelope; socket diagnostics remain implementation/inspector data unless product-facing.

Example:

```kotlin
interface CricketQueries {
    fun liveScore(matchId: MatchId): Flow<ResourceObservation<LiveScore>>
    fun match(matchId: MatchId): Flow<ResourceObservation<Match>>
    fun commentary(matchId: MatchId): Flow<ResourceObservation<List<Commentary>>>
}
```

---

# 14. Resource architecture

## 14.1 Core rule

Shared product/resource truth belongs below presentation.

Presentation observes product-facing Capability Queries. Resource implementation remains hidden.

## 14.2 Snapshot Resource

Typical current architecture:

```mermaid
flowchart LR
    Q[Capability Query] --> OBS[SyncCoordinator.observing]
    OBS --> LOCAL[Cold local Flow]
    LOCAL --> SQL[SQLDelight rows + sync marker]
    OBS --> SYNC[Capability sync]
    SYNC --> HTTP[REST]
    SYNC --> SQL
    OBS --> ST[SyncCoordinator.status]
    ST --> Q
```

`SyncCoordinator` is owned runtime infrastructure, not a Capability API. It coordinates work and
status; the database owns the value.

## 14.3 Snapshot semantics

A Capability defines:

- what the resource identity is;
- which durable rows and sync marker make up the value;
- whether a retained value is shown while a sync runs or after it fails;
- explicit refresh behavior;
- offline behavior;
- mapping/error semantics.

The common coordinator must not invent those semantics globally.

## 14.4 Live Resource

```mermaid
flowchart TD
    A[Cell A<br/>FeatureInstanceKey A] --> Q[CricketQueries.liveScore]
    B[Cell B<br/>FeatureInstanceKey B] --> Q
    Q --> R[LiveScoreResource<br/>ResourceKey match-123]
    R --> C[LiveScoreCoordinator<br/>subscriber count + connection lifecycle]
    C --> WS[WebSocket]
    C --> WRITER[Shared resource writer]
    WRITER --> DB[SQLDelight / resource state]
    DB --> R
```

Required semantics per ResourceKey:

```text
0 subscribers
  -> no active connection after grace/background policy

first subscriber
  -> ensure current/bootstrap state
  -> connect if policy requires

additional subscribers
  -> share existing resource/connection

socket event
  -> validate/sequence/reconcile
  -> write through shared resource path

last subscriber leaves
  -> grace policy
  -> disconnect/release
```

Reconnect/backoff belongs to the Capability/live-resource owner.

## 14.5 The sync coordinator is not the UI event/lifecycle layer

The coordinator joins sync work per key, counts observers, and records process-local status over a
Capability-owned durable source of truth. It does not own presentation lifecycle.

Do not use it as:

- Cell lifetime runtime;
- UiCommand bus;
- navigation state;
- peer presentation event bus;
- generic WebSocket subscriber ownership framework.

## 14.6 REST + live write convergence

REST bootstrap/refresh and WebSocket updates can race.

The Capability Resource must define a single write/reconciliation path appropriate to the product, for example:

- server version/sequence;
- timestamp/revision policy;
- socket event sequence;
- source priority only if domain-correct.

The Resource Inspector should expose last write origin and, where available, socket/revision sequence.

Do not leave competing presentation-level mutable copies to resolve races accidentally.

## 14.7 Projection

Projection derives from owned resources rather than copying them.

Late subscribers should converge on current underlying state.

Use named/materialized projection only when repeated expensive combines/joins justify it.

## 14.8 Single-owner rule for expensive mutable resources

| Resource | Authoritative owner | Consumers | Cleanup policy |
|---|---|---|---|
| live-score connection | keyed LiveScoreCoordinator | Cricket resources/queries | subscriber + background/grace policy |
| video/audio players | PlaybackCoordinator/player pool | Video capability/widgets | viewport/audio focus/pool/release |
| batch request queue | BatchRefresh/Hydration coordinator | multiple capability writers | size/time/deadline flush |
| upload/download queue | TransferCoordinator | Commands/features | persist/recover if required |
| location session | location coordinator | Capability consumers | permission/foreground/subscriber policy |

Presentation expresses interest/intent; it does not directly construct or retain the shared resource.

---

# 15. Refresh orchestration and batching

## 15.1 Split responsibilities

### Capability owns

- resource identity;
- when a key is due and what a retained value means;
- Snapshot resource behavior;
- invalidation;
- retry policy;
- whether a trigger actually requires work.

### Common orchestration owns

- foreground trigger;
- reconnect trigger;
- visibility trigger;
- pull-to-refresh trigger;
- periodic trigger execution;
- trigger diagnostics;
- generic concurrency/backpressure/network policy.

## 15.2 Declarative hook example

```kotlin
refreshHooks.register(
    trigger = RefreshTrigger.Foreground,
) {
    articleSync.syncIfDue(ArticleKey.All, RefreshQos.background())
}
```

The common layer says an opportunity occurred. The Capability says whether it is needed.

## 15.3 Pull-to-refresh

User-forced refresh is semantically distinct from passive due-checking.

```text
Foreground -> syncIfDue(key, RefreshQos.background())
PullToRefresh -> sync(key, RefreshQos.visible())
```

## 15.4 Domain-blind QoS

Generic priority:

```text
CRITICAL_VISIBLE
VISIBLE
BACKGROUND
PREFETCH
```

Generic network policy:

```text
ANY_NETWORK
UNMETERED_PREFERRED
UNMETERED_ONLY
```

Scheduler may own:

- max concurrent work;
- queue/backpressure;
- cancellation;
- identical-work dedupe/single-flight;
- data-saver/metered policy;
- background/battery policy.

Scheduler must not become a product-domain brain.

## 15.5 Why not a global policy-heavy RefreshCoordinator

A giant global coordinator would create a second resource framework beside the owned Snapshot runtime and SQLDelight, then centralize product semantics in a common manager.

Helix centralizes **external opportunity + generic scheduling**, not domain freshness policy.

## 15.6 BatchHydrator

Example:

```text
HomeHydrator
  -> POST /home/batch
      -> cricket payload -> Cricket resource writer
      -> article payload -> Article resource writer
      -> promo payload   -> Promo resource writer
```

Rules:

```text
MAY fetch aggregate BFF response
MAY translate pieces
MAY feed owning capability/resource writers
MAY use one transaction where correct

MAY NOT own observable application state
MAY NOT become a Query surface
MAY NOT bypass capability ownership semantics
SHOULD auto-register with debug Resource Inspector
```

Presentation consumers continue observing Capability Queries and do not know batching occurred.

---

# 16. Commands, mutations, outbox, and idempotency

## 16.1 Simple command

Use direct remote/local command when durability/retry correctness does not require an outbox.

```text
Action
 -> ViewModel
 -> Command
 -> capability impl
 -> API / DB
 -> update or invalidate resources
```

## 16.2 Optimistic command

When immediate feedback is required:

```text
write optimistic owned state
 -> send command
 -> reconcile confirmation/failure
```

The optimistic state still has one authoritative owner.

## 16.3 Durable outbox

Use only when user intent must survive:

- offline periods;
- process death;
- ambiguous network response;
- delayed background delivery.

Pattern:

```text
local transaction
  - update durable local state
  - insert PendingCommand(commandId, payload, status)

sync coordinator
  - send/retry idempotently
  - confirm/reconcile
  - clear/mark command
```

Track where useful:

- stable CommandId/idempotency key;
- attempt count;
- error category;
- created time;
- next retry;
- permanent failure state.

Do not route every POST through an outbox.

---

# 17. Durable storage and database topology

## 17.1 SQLDelight role

SQLDelight is the current qualified durable relational store.

Rules:

- generated query/row types remain implementation details;
- Capability implementations map DB types to product/domain models;
- each Capability implementation owns its `.sq`/`.sqm` files and generated schema interface;
- `:storage:database` composes contributors, drivers, and opening, then verifies the merged
  migration sequence from its checked-in snapshot;
- assembly-owned `.sq` may express cross-capability joins/projections;
- migration versions are unique across every schema-contributing module;
- presentation never depends on SQLDelight.

### 17.1.1 File and table naming

- one `<Name>Schema.sq` per Capability under its `db/` package; the generated accessor is
  `<name>SchemaQueries`, which cannot collide with the Capability's `<Name>Queries` API;
- table names are camelCase (SQLDelight's own example idiom), so generated row classes are
  PascalCase;
- generated row/query types stay inside the local source class; the `.sq` file name has no schema
  effect, while a table rename is a `.sqm` migration.

## 17.2 One physical database is a default, not a law

Starting topology:

```text
:capability:<a>-impl/db --\
:capability:<b>-impl/db ---> :storage:database/AppDatabase -> one physical database
:capability:<c>-impl/db --/
```

Logical schema ownership remains capability-specific while storage owns the assembled database's
physical lifecycle. One database is the default; the hard product budget is at most five, and an
additional database requires a genuinely different lifecycle.

Do not split databases pre-emptively.

Revisit physical topology if evidence shows:

- migration serialization/ownership contention;
- invalidation fanout;
- performance/isolation need;
- schema co-change patterns;
- team ownership conflicts;
- build/test blast radius.

## 17.3 Known SQLDelight costs

- relational mapping from non-relational server payloads is real code;
- table/query invalidation can be coarser than entity-normalized caches;
- migrations cross multiple product areas when one physical DB is shared;
- Web/Wasm driver behavior is version-sensitive.

These are managed costs, not reasons to expose DB types upward.

## 17.4 Preferences and secure storage

Generic key-value mechanism may live in Foundation/Platform.

Product semantics such as notification preferences or user settings belong in a Capability.

Secure tokens/secrets use platform secure-storage APIs and Identity/session ownership.

---

# 18. Technical mechanisms and current library choices

Helix architecture is stable across library replacement. This section records the current baseline and alternatives.

## 18.1 Compose Multiplatform

**Choice:** Compose Multiplatform across Android, iOS, Desktop, Web/Wasm.

Why:

- shared UI/presentation implementation;
- Navigation 3 support on non-Android targets from Compose Multiplatform 1.10;
- common preview/tooling improvements;
- fits the current product's cross-platform strategy.

Known Web caveat:

Reconsider if Web becomes a first-class independently optimized product requiring SEO, DOM integration, accessibility, or web-native capabilities that Compose/Wasm cannot reasonably satisfy.

## 18.2 Navigation 3

**Choice:** Navigation 3, with the exact implementation pin owned by the repository version catalog.

Historical qualification note: the architecture POC used the then-selected 1.1.x line and validated the required back-stack/ViewModel behavior. Do not interpret that historical pin as the current upstream release.

Internal qualification:

- back-stack behavior passed;
- deep-link/URL mapping POC passed under the project integration;
- entry-scoped ViewModel destruction passed;
- Web browser-history remains a permanent qualified seam.

Qualification policy:

- exact Nav3/CMP versions come from the repository version catalog;
- target support/browser-history behavior is checked against the upstream sources in [Section 38, now in `background.md`](background.md#38-public-reference-urls-used-for-qualificationreasoning) at upgrade time;
- browser Back/Forward/URL/direct-entry/refresh/deep-link behavior remains permanently regression-qualified.

```text
Navigation 3 runtime behavior:        qualified at repository pin
Nav3 CMP support:                     qualified at repository pin
Nav3 browser-history integration:     QUALIFIED-WITH-WARNING
```

Required Web regression suite:

- Back;
- Forward;
- URL sync;
- direct URL;
- refresh;
- deep link.

### Why not Decompose now

See history and ADRs. It provides more component runtime than current product requirements need.

## 18.3 AndroidX ViewModel

**Choice:** default Screen/Cell presentation owner.

Why:

- familiar;
- testable;
- constructor DI friendly;
- works with NavEntry owner + qualified keyed embedded owner;
- avoids adding another presentation runtime.

### Why not Circuit/Molecule by default

Their presenter/state-production models influenced the desire for lower ceremony and local state production, but another presentation runtime is not needed to satisfy current ownership semantics.

Revisit if measured ViewModel/presentation ceremony dominates useful logic and a mature alternative materially deletes code across all required targets.

## 18.4 Owned sync coordinator

**Choice:** `SyncCoordinator<Key>` in `:foundation:resource-runtime`, internal to Capability
implementation and owned by this project. The database owns the value.

The revisit condition fired twice. On 2026-09-04 the first representative Posts implementation
compared the third-party Store5 wiring with a small owned network-bound-resource reference. With
its cache disabled, Store5 supplied only fetch deduplication and a source-of-truth barrier while the
project still needed converters in both directions, a snapshot plus validator wrapper for the
never-synchronised state, freshness inferred from read origin, a mutex that queued duplicate network
calls, duplicated grace timeouts, and per-post lease, eviction, and cancellation machinery. The
owned `SnapshotResource` that replaced it kept a per-key confirmed value and derived freshness by
comparing durable emissions with a completion re-read. On 2026-09-05 its release-gate review showed
that this made the runtime a second value owner next to SQLDelight: it needed invalidation
generations, self-heal syncs, structural equality on every emission, and re-read gates to stay
consistent, and a detail write to a shared table could still change the feed's freshness without any
feed sync. ADR-43 removed the value from the runtime.

Historical POC provenance: Store5 was adopted as the Kotlin analogue of TanStack Query, whose
observer-driven fetching, in-flight deduplication, and reconnect refetch shaped the resource
contract in 13.7. The architecture experiment used Store5 `5.1.0-alpha10` for cache-first reads,
explicit refresh, offline startup, a SQLDelight source of truth, two observers, and REST plus live
update integration. That evidence remains history, not current implementation guidance.

The coordinator keeps the public observation shape: `ResourceObservation` lost its freshness field
and `SyncStatus` was added. It provides per-key worker joining, due-checking with a minimum
interval, observer counting around a Capability-supplied flow, an offline retry of observed keys on
reconnect, caller-cancellation isolation, and a bounded status ledger. SQLDelight remains the only value owner;
a Capability persists its own synchronized marker in the same transaction as the rows. Live
resources still use their own keyed shared stream and coordinator.

Revisit if measured diagnostics, correctness, target support, or repeated Capability-specific
policy show that this narrow runtime no longer earns its ownership cost.

## 18.5 SQLDelight

**Choice:** SQLDelight, with the exact production pin owned by the repository version catalog.

Historical POC provenance: the validated Web Worker/durable-storage experiment used `2.2.1`.

POC evidence:

- required Android/iOS/Desktop behavior;
- actual browser Web Worker path validated for Web/Wasm target integration;
- migrations/query/driver smoke behavior retained as qualification suite.

Official 2.2.1 web-worker docs require asynchronous database generation for the web worker driver.

Revisit on target support/performance/tooling failure or if another KMP durable store materially improves requirements.

## 18.6 Ktor

**Choice:** generic network mechanism.

Ownership split:

```text
:foundation:network
  -> HttpClient construction
  -> serialization configuration
  -> timeouts
  -> common request IDs/interceptors
  -> auth mechanics (per-request opt-in)
  -> retry policy (per-request opt-in)
  -> failure model
  -> generic connectivity/telemetry hooks

Capability Impl
  -> product endpoints
  -> DTOs
  -> mapping
  -> repository/resource behavior
```

Features do not depend on Ktor clients directly.

## 18.6.1 Auth/session/network ownership

Authentication has cross-cutting mechanics but one product owner.

### Identity owns product/session semantics

The Identity Capability owns:

- signed-in / signed-out session state;
- access/refresh credential lifecycle;
- single-flight token refresh semantics;
- logout/sign-out;
- terminal session-expiry decision;
- secure credential persistence semantics through Platform secure storage.

### Foundation network owns generic HTTP mechanics

Foundation network may define a neutral inversion such as:

```kotlin
interface CredentialProvider {
    suspend fun currentCredential(): String?
    suspend fun refreshCredential(rejected: String?): CredentialRefreshResult
}

sealed interface CredentialRefreshResult {
    data class Refreshed(val credential: String) : CredentialRefreshResult

    /** The credentials are invalid; the provider has already applied its session semantics. */
    data object Rejected : CredentialRefreshResult

    /** Refresh could not run right now (offline, server down); the session is unchanged. */
    data object Unavailable : CredentialRefreshResult
}
```

Identity implementation supplies it through App composition.

[`network.md`](network.md) owns the HTTP client assembly and plugin mechanics.

Refresh returning `Rejected` or `Unavailable` surfaces the original 401 to the caller; the client
never retries with a credential it knows to be invalid.

The authenticated Ktor client may:

1. request current bearer token;
2. inject it generically;
3. on eligible `401`, invoke single-flight refresh;
4. retry the original request according to policy;
5. send refresh requests through the same client without `authenticated()`.

Product endpoints, DTO mapping, and business semantics remain in owning Capability implementations.

### Terminal session end

Correctness-bearing session truth lives in `:capability:identity-api`:

```kotlin
sealed interface SessionState {
    data object SignedOut : SessionState

    data class SignedIn(
        val accountId: AccountId,
    ) : SessionState
}

interface IdentityQueries {
    fun session(): StateFlow<SessionState>
}
```

The optional cross-cutting event contract also lives in **Identity API**, not Identity Impl:

```kotlin
enum class SessionEndReason {
    EXPIRED,
    REVOKED,
    SIGNED_OUT,
}

sealed interface IdentityEvent {
    data class SessionEnded(
        val reason: SessionEndReason,
    ) : IdentityEvent
}

interface IdentityEvents {
    val events: Flow<IdentityEvent>
}
```

Terminal transition:

```text
Identity Impl
  -> clears authoritative credential/session state
  -> updates IdentityQueries.session() to SignedOut
  -> may publish IdentityEvent.SessionEnded
```

App/navigation correctness follows `SessionState`, not transient event delivery. Other Capabilities that must guarantee user-scoped cleanup must also reconcile from the durable/observable session owner (or an explicit logout orchestration path); they must not rely solely on seeing `SessionEnded`.

`SessionEnded` remains useful as a typed happened-fact for independent best-effort reactions/diagnostics. It is not a global string EventBus.

Other Capability implementations may depend on `:capability:identity-api`; they do not import Identity implementation just to obtain tokens.

## 18.6.2 Backend contract generation

When the backend publishes an authoritative, CI-maintained OpenAPI (or equivalent) contract, prefer generated transport DTO/client definitions inside Capability implementation.

Rules:

- generated transport code stays implementation-only;
- map generated models to stable product/domain models before Capability API;
- CI detects contract/code-generation drift;
- generated files are not hand-edited;
- do not force generation when the backend contract is stale/non-authoritative.

This is a recommended anti-drift mechanism, not a universal Helix law.

## 18.7 Koin and Koin compiler/graph validation

**Choice:** Koin for DI, with constructor injection, compiler validation for typed definitions, and
runtime verification of the assembled graph.

Rules:

- App composition root assembles graph;
- objects use constructor injection;
- avoid `KoinComponent`/global `get()` in business/data classes;
- Features depend on interfaces/product types, not implementation selection.

Current state, qualified on 2026-09-06:

- The entry point's module list is literal. `startKoin` in `KoinApp.kt` names every module, one
  bare identifier per line, and nothing else. That literal list is what the Koin compiler plugin
  reads: with it, the plugin resolves the whole graph and validates **every typed definition in
  every module reachable from the entry point**. Any function call, spread, variable, or inline
  `module { }` in that argument list makes the list dynamic again; the plugin reports `KOIN-W003`
  and silently falls back to a fail-open pass over whatever it happened to discover, while every
  gate still passes. `KoinApplicationModulesRuleTest` (§21.4) holds the shape.
- The one runtime value the graph cannot compute for itself, the build flag, enters as a Koin
  property (`app.isDebug`) rather than as a definition written inside the entry point, which would
  make the list dynamic. `environmentModule` builds the single `AppEnvironment` from that property.
  Koin's own logger is the one consumer that exists before the graph does; it is installed one step
  later, from the environment the graph built, so the process keeps one `AppEnvironment` and one
  app `Logger`. The price is Koin's own "loaded N definitions" line, emitted while its logger is
  still the empty default.
- What the compiler validates: typed definitions, `single<T>()` and `viewModel<T>()`, across the
  whole graph. Removing `single<TodosSettingsSource>()` from `todosCapabilityModule` fails
  `:app:shared:compileKotlinJvm`, verbatim:

  ```text
  e: [Koin][KOIN-D001] Missing dependency: dev.mayankmkh.basekmpproject.capability.todos.impl.TodosSettingsSource
    required by: dsl:dev.mayankmkh.basekmpproject.capability.todos.impl.TodosCapabilityImpl (parameter 'settingsSource')
    in module: dev.mayankmkh.basekmpproject.capability.todos.impl.todosCapabilityModule
  ```

- What it does not validate: anything inside a definition lambda. Plugin 1.1.0 never looks into a
  lambda body, `single { create(::fn) }` included, so rewriting a lambda in that shape buys
  nothing. Removing the `HttpClientEngine` definition still compiles clean; `KoinGraphTest` is what
  fails, verbatim:

  ```text
  org.koin.test.verify.MissingKoinDefinitionException: Missing definition for '[field:'engine' - type:'io.ktor.client.engine.HttpClientEngine']' in definition '[Singleton: 'io.ktor.client.HttpClient']'.
  ```

  Runtime parameters (`viewModel { parameters -> ... }`) are outside the compiler's reach for the
  same reason.
- The convention plugins apply the plugin only to `bkp.kmp.app`, `bkp.kmp.feature`, and
  `bkp.kmp.capability.impl`, the roles that own Koin definitions or the application entry point.
  Desktop and web app launchers call `initKoin` but declare no Koin DSL, so their roles do not apply
  it.
- Koin 1.1.0 does not validate leaf compilations independently: they print
  `w: [Koin] compile-safety validation skipped -- no Koin entry point in this compilation.` Feature
  and Capability Impl typed definitions publish hints and are checked only when the `:app:shared`
  compilation reaches its `startKoin { }` entry point.
- One warning remains, on every compilation the plugin touches, verbatim:

  ```text
  w: Koin compiler plugin: Kotlin 2.4.10 is newer than the newest tested version (2.4.0) — proceeding with the 2.4.0 adapter. If compilation fails, check for a koin-compiler-plugin update. Supported versions: 2.3.20, 2.4.0.
  ```

  It stays. The plugin offers options to silence it, and silencing a "this toolchain is untested"
  notice is exactly the wrong trade: the next Kotlin bump is when it matters. Under
  `-PwarningsAsErrors=true` it is reported as `e:` and fails every module the plugin is applied to.
  The property is not usable on this repository anyway: `:foundation:resource` already fails it on
  an unrelated unresolved opt-in marker, so the default builds are the qualified ones.

Runtime checks stay, because they cover what the compiler cannot. `KoinGraphTest` starts the real
entry point and reads back the definitions the running instance loaded, so there is no second
module list to drift: `verify()` reaches the constructors behind lambda definitions, ViewModels
with runtime parameters included, and the root-resolution test runs the definition bodies
themselves, which the compiler never does.

No KSP or `koin-annotations` dependency is used. Typed definitions import the compiler DSL from
`org.koin.plugin.module.dsl` under its own name: `single<T>()` takes no lambda, so it never competes
with `Module.single { }` and needs no import alias. Aliases, runtime parameters, environment-based
definitions, custom factories, and lifecycle hooks that do work stay in the classic lambda DSL.

### Definition lifetimes

Koin has no default lifetime; every definition names one, and the choice is a design decision, not
a habit. The rules, in the order to try them:

- `single` for a shared handle or a stateful owner whose identity matters: the HTTP client and its
  engine, the database driver provider, the store factories, the connectivity monitor, the runtime
  scope, and every Capability implementation with its sources. A singleton holds handles and
  coordinators, never data. Data lives on disk or in a lease-evicted resource (§13.7), so an idle
  Capability costs a few small objects and nothing the collector would want back. Koin creates a
  singleton on first request, so a Feature the user never opens costs nothing.
- `viewModel` for presentation state: one instance per Screen or Cell instance, keyed by
  `FeatureInstanceKey` and cleared by the ViewModelStore that owns it (§12).
- `factory` for a stateless or per-caller object that is cheap to build: a use case, a mapper, a
  validator. The graph has none today because Capability implementations carry their own
  operations; the first one that grows past comfortable reading is the signal to split operations
  into `factory` definitions rather than to widen the singleton.
- A Koin scope for a lifetime with an explicit open and close that several definitions share. The
  expected first use is a user session: an authenticated client, per-user caches and the user id
  opened at login and closed together at logout, instead of every holder resetting itself. Do not
  introduce a scope for a lifetime a ViewModel or a lease-evicted resource already owns.

Reviewers check two things: a `single` that stores data in a field instead of a resource, and a
`factory` that reopens something a singleton should own.

### Why not Koin Annotations or Metro now

This is measured, not assumed. Koin Annotations and Metro 1.4.2 were both built out on this
repository and this toolchain on 2026-09-06, as working spikes rather than paper comparisons. Both
close the remaining compile-time gap: whole-graph validation that includes custom providers, and in
Metro's case runtime parameters as well. Both were declined for the same three reasons. They put
the injector back into business classes (`@Single`, `@Inject`) or into hand-written graph
interfaces, which is precisely what "avoid `KoinComponent`/global `get()` in business/data classes"
exists to prevent. They remove `onClose` on a definition in favour of a hand-written close registry,
turning a declared lifetime into bookkeeping. And both add generated indirection that a reader, or
an agent, has to hold in their head before they can follow a wire. What they would have added on
top of the current setup is already covered: the runtime root-resolution test executes every
definition body over the real graph.

`kotlin-inject-anvil` is not a live contingency because upstream declares maintenance mode. The
written contingency is Metro 1.4.2, published to Maven Central on 2026-08-14. It is a compiler
plugin with no KSP step, provides wasmJs and iOS runtimes, and offers
`metrox-viewmodel-compose` for Compose Multiplatform, including wasmJs.

Revisit Metro if the Koin compiler plugin cannot be qualified on the pinned Kotlin version or
blocks a Kotlin upgrade for more than one minor release. Revisit the DI choice more broadly if
another KMP option materially reduces owned construction and verification complexity.

## 18.8 Kermit and OpenTelemetry

**Choice:** stable semantic interfaces with Kermit default; optional OpenTelemetry/vendor adapters by qualified target.

```text
FeatureObserver
  -> KermitObserver
  -> DebugInspectorObserver
  -> RecordingTestObserver
  -> optional OpenTelemetryObserver
```

Separate:

```text
ProductAnalytics
```

Historical POC qualification found the selected OpenTelemetry Kotlin version did not provide the required Wasm path, so Web used neutral/Kermit instrumentation.

Current OpenTelemetry Kotlin remains a developing project; target support/version changes are an adapter qualification concern, not a Feature API concern.

### Logging

One Kermit `Logger` is configured at the App composition root and flows through Koin. Product code never resolves Kermit's global companion object.

```text
minimum severity   Verbose in a debug build, Warn otherwise
tag                the module's own name, applied where the module receives the logger
iOS writer         Kermit's Xcode-tuned default in debug; OSLog with subsystem = the application id in release
Koin diagnostics   the same logger, on every target
Features           do not log at all
```

`initKoin` requires the entry point's own debug signal. It enters the graph as a Koin property, the
graph builds the one `AppEnvironment` from it, and Koin's own logger is installed from that
environment after the modules load, so every verbosity gate (Kermit, Koin, Ktor) derives from one
decision and one instance. Koin's own "loaded N definitions" line is emitted before that logger
exists; see §18.7.

Tags: a module that owns a logging seam tags the logger in its own entry point (`CommandBridge`,
`preferenceStores`, `secretStores`); the app tags the loggers it hands to third-party adapters
and to its own runtime (`HTTP`, `koin`, `runtime`).

Lines are structured: `logEvent(name, "key" to value, ...)` in `:foundation:runtime` renders
`name key=value ...`, so every module writes the same shape. A line never carries secrets, key
material, file contents or user data; an exception message is included only where the exception is
known not to quote them (network failures yes, store corruption no).

Features report Outputs and state, never log lines (§19.2). A module below presentation logs only
where a decision would otherwise be invisible, and §19.7 governs what may appear in the line.

## 18.9 Coil image loading

Start with one reusable image UI mechanism, for example `:ui:image`:

- shared application `ImageLoader`;
- placeholders/errors;
- accessibility defaults;
- preview/test behavior;
- observability hooks.

Coil/vendor types should not leak into unrelated Feature/Capability APIs.

Split deeper image-loader infrastructure only if real needs appear:

- non-Compose image loading;
- prefetch;
- authenticated requests;
- custom decoders;
- multiple loader policies;
- deep image-pipeline telemetry.

## 18.10 Preferences

Generic preferences mechanism belongs in Foundation; product-specific settings belong in Capabilities.

Do not scatter raw preference keys through Features.

[`preferences.md`](preferences.md) owns the preferences, document and secure-storage mechanics.

## 18.11 Platform services

| Mechanism | Platform home | Product meaning |
|---|---|---|
| Share sheet | `:platform:sharing-api/impl` | content-sharing command/analytics |
| Notifications | `:platform:notifications-api/impl` | notification Capability/inbox/preferences/routing |
| Permissions | `:platform:permissions-api/impl` | Feature/Capability policy |
| File picker | `:platform:file-picker-api/impl` | upload/import behavior |
| Secure storage | `:platform:secure-storage` (single module, §7.17) | Identity/session Capability |
| Background work | `:platform:background-work-api/impl` | sync/download/reminder Capability |

## 18.11.1 Product configuration, flags, experiments, design system, and localization

### Product configuration / remote flags

Typed product flags, kill switches, experiment assignment, and remote variants belong to a ProductConfiguration/Experiments Capability (name may follow product language).

- vendor Remote Config/experimentation SDK types and raw string keys stay in implementation;
- presentation consumes typed product concepts;
- kill-switch exclusion occurs at composition/registry before Cell instantiation where practical;
- raw flag reads do not scatter through Cell internals.

### Design system / theme

Shared visual tokens and stateless visual primitives belong in a UI/design-system responsibility, for example:

```text
:ui:design-system
```

It owns typography, spacing, colors/theme tokens, shared stateless primitives, and genuinely visual accessibility defaults. Product/business rules do not move into the design system.

### Localization

Shared locale/plural/formatting/resource mechanisms may live in:

```text
:ui:localization
```

Feature-specific strings may remain with the Feature when cohesive.

Rules:

- localized display strings are never domain identifiers;
- domain/business logic receives stable typed values, not translated text;
- locale-sensitive formatting that changes business meaning belongs to the relevant Capability.

## 18.11.2 Durable outbox/background replay across targets

Durable Command/outbox ownership remains with the owning Capability.

Portable in-process retry opportunities:

```text
app foreground
network reconnect
explicit user retry
periodic opportunity while process is alive
```

Background execution is best-effort Platform machinery:

```text
Android -> platform background work implementation
iOS     -> best-effort background scheduling when product/OS permits
Desktop -> application/OS scheduling only when justified
Web/Wasm-> never assume reliable execution after page/process exit
```

Platform background work may wake/request the Capability runner; it does not own outbox state or business retry semantics. Correctness must tolerate background scheduling not running; the next foreground/reconnect resumes durable work.

## 18.12 No generic utils module

A helper remains local until it has a coherent reusable responsibility.

When promoted, name the responsibility:

```text
:foundation:time
:foundation:presentation
:ui:localization
:capability:content-sharing
```

If it can only be named `utils`, `misc`, `helpers`, or `common-utils`, keep it local until the responsibility is clear.


---

# 19. Observability, diagnostics, analytics, and the flight recorder

## 19.1 Stable seams

Product/presentation code depends on semantic internal contracts, not telemetry vendors.

Example conceptual contracts:

```kotlin
data class FeatureIdentity(
    val feature: String,
    val instance: FeatureInstanceKey,
    val route: RouteKey? = null,
)

interface FeatureObserver {
    fun created(identity: FeatureIdentity)
    fun action(identity: FeatureIdentity, name: String)
    fun stateChanged(identity: FeatureIdentity, stateCategory: String)
    fun uiCommand(identity: FeatureIdentity, name: String)
    fun output(identity: FeatureIdentity, name: String)
    fun destroyed(identity: FeatureIdentity)

    suspend fun <T> operation(
        identity: FeatureIdentity,
        name: String,
        block: suspend () -> T,
    ): T
}

interface ProductAnalytics {
    fun track(event: ProductEvent)
}
```

Exact method shapes may evolve. The stable architectural separation is what matters.

### 19.1.1 Non-generic Feature trace delegate

Rejecting a universal `FeatureViewModel<State, Action, ...>` superclass does not mean each ViewModel should hand-write observer plumbing.

Provide a tiny non-generic helper that binds `FeatureIdentity` once:

```kotlin
class FeatureTrace(
    private val identity: FeatureIdentity,
    private val observer: FeatureObserver,
) : AutoCloseable {
    init { observer.created(identity) }

    fun action(name: String) = observer.action(identity, name)
    fun state(category: String) = observer.stateChanged(identity, category)
    fun uiCommand(name: String) = observer.uiCommand(identity, name)
    fun output(name: String) = observer.output(identity, name)

    suspend fun <T> operation(
        name: String,
        block: suspend () -> T,
    ): T = observer.operation(identity, name, block)

    override fun close() = observer.destroyed(identity)
}
```

Generator/reference ViewModels wire it consistently and close it with ViewModel lifecycle.

The helper owns no product state, has no State/Action generic parameters, is not a ViewModel superclass, and is replaceable if the runtime later provides an equivalent interception seam.

A small factory keeps identity construction consistent:

```kotlin
fun interface FeatureTraceFactory {
    fun create(
        feature: String,
        instanceKey: FeatureInstanceKey,
        route: RouteKey? = null,
    ): FeatureTrace
}
```

The factory/observer implementation is supplied by App/observability composition; Features do not construct vendor telemetry adapters.

## 19.2 Why analytics and operational telemetry are separate

Operational telemetry answers:

- what happened in the software?
- how long did it take?
- which owner/resource/connection was involved?
- where did an error occur?

Product analytics answers:

- what product behavior did the user perform?
- which business funnel/event occurred?

Do not make Feature code call Firebase/OTel/Kermit/vendor SDKs directly.

## 19.3 Trace path

A debug trace should be able to reconstruct:

```text
UI Action
  -> Screen/Cell ViewModel
  -> Capability Query/Command
  -> Store/Repository/Coordinator
  -> SQL / HTTP / WebSocket
  -> resource write/emission
  -> ViewModel State
  -> Compose rendering
```

## 19.4 Resource Inspector

The Resource Inspector / flight recorder is a first-class AI/QA/debug architecture seam.

It should know resources automatically wherever possible.

Preferred factory pattern:

```kotlin
createResourceStore(...)
createLiveResource(...)
createBatchHydrator(...)
```

Debug binding:

- auto-registers metadata;
- captures resource lifecycle summaries.

Release binding:

- may be no-op/minimal as required.

## 19.5 Inspector information

Useful debug fields:

```text
Feature / Screen / Cell
FeatureInstanceKey
RouteKey
ResourceKey
resource type: Snapshot | Live | Projection
observers/subscriber count
source of truth
freshness / snapshot age
in-flight refresh
last REST refresh
live connection status
reconnect count
last write origin
server/socket sequence/revision where available
which FeatureInstanceKeys observe the resource
pending/outbox command count
last Action
last Output
current state category
active operation
```

## 19.6 Example inspector view

```text
LiveScoreCell
---------------------------------------
feature             cricket
instance            home-feed:score:123
route               home
resource             match-123
resource type        LIVE
subscribers          2
presentation owner   retained keyed item
state                Live
last action          Refresh
last output          OpenMatch
freshness            1.2s
socket               connected
reconnects           1
last write           websocket seq=9192
last REST refresh    28s
```

## 19.7 Telemetry safety

Do not serialize full ViewModel/domain/resource state into production telemetry by default.

Prefer:

- state categories;
- durations;
- result/error categories;
- safe resource type/owner metadata;
- bounded correlation identifiers where privacy-reviewed;
- trace context rather than high-cardinality metric labels.

Avoid user IDs/article IDs/match IDs/instance UUIDs as uncontrolled metric dimensions.

Apply:

- consent;
- redaction;
- sampling;
- data minimization;
- offline buffering policy;
- retention policy

at the appropriate adapter/export layer.

---

# 20. Testing and quality architecture

## 20.1 Principle

Every important architectural boundary should have an executable oracle.

No single test type is the specification.

During the **first roughly 10 real production Cells after adoption**, testing/ergonomics data is also architecture evidence: measure reading/ceremony/context/retry/debug cost rather than assuming the design is already production-optimal.

```mermaid
flowchart TD
    A[Compile / type system] --> B[Architecture graph and source rules]
    B --> C[Owner State/Action tests]
    C --> D[Capability/resource tests]
    D --> E[Integration/lifecycle/navigation tests]
    E --> F[Accessibility + visual fixtures/goldens]
    F --> G[Critical E2E workflows]
    G --> H[Runtime observability / production evidence]
```

## 20.2 What each layer proves

### Compiler/module graph

Proves structural legality such as dependency direction.

### Architecture/source tests

Proves rules such as:

- UI imports no ViewModel/Koin/navigation/Capability;
- Capability API imports no infrastructure;
- ViewModel visibility/public API rules;
- UiCommand payload restrictions where statically knowable;
- no forbidden peer Cell coupling.

### Presentation-owner tests

Prove:

- initial State;
- resource -> State projection;
- Action handling;
- local presentation state;
- UiCommand semantics;
- Output semantics.

### Capability/resource tests

Prove:

- cache/SSOT behavior;
- mapping;
- freshness/refresh;
- offline behavior;
- command reconciliation;
- batching;
- concurrency;
- live-resource sharing/reconnect;
- outbox/idempotency.

### Runtime tests

Prove:

- NavEntry destruction;
- back/deep-link/browser behavior;
- keyed Cell offscreen retention;
- logical removal cleanup;
- adaptive pane ownership;
- process/configuration recovery.

### Fixtures/goldens

Prove rendering changed or remained stable for explicit fixture states.

They do **not** prove business correctness.

### Accessibility

Proves semantic/readability/accessibility expectations separately from pixels.

### E2E

Proves high-value user workflows across boundaries.

## 20.3 Test expectations by role

| Role | Typical required evidence |
|---|---|
| UI | fixture/golden states where valuable, accessibility semantics, callback tests |
| Feature | ViewModel State/Action/UiCommand/Output, content integration, fixtures |
| Capability API | contract examples/fakes where useful |
| Capability Impl | resource/command/repository/cache/socket/batching/mapping/failure/concurrency |
| Storage | SQL behavior, migrations, transactions |
| Runtime | navigation, saved state, adaptive, keyed lifetime cleanup |
| Architecture | graph, source rules, ABI, DI graph |

## 20.4 Fakes

Prefer direct fake Capability contracts and fake Feature state over booting the full production DI graph for every test.

Shared generic helpers -> `:testkit:*`.

Feature-specific scenarios stay beside the Feature.

## 20.5 Goldens are deliberately narrow

Goldens answer:

> Did the rendering for this fixture change?

They do not answer:

- is auth correct?
- did the Command persist?
- is architecture legal?
- did refresh dedupe?
- is the live socket shared?

A human approving a visual diff cannot override a failed behavioral or architecture check.

## 20.6 Framework-upgrade golden churn

Compose/font/rendering upgrades may create broad expected visual changes.

Where practical:

- isolate framework-upgrade golden refreshes from product feature changes;
- generate before/after evidence;
- keep behavioral tests independent;
- replace noisy golden tooling if churn destroys review value.

## 20.7 Fixture gallery as non-dev review surface

A PM/designer/QA contributor should be able to inspect meaningful Cell states without:

- configuring a backend;
- navigating hidden app paths;
- knowing Android lifecycle;
- editing production data.

## 20.8 High-value live-resource tests

At minimum:

1. first observer opens/joins resource;
2. second observer shares same resource/connection;
3. both receive current state;
4. REST/bootstrap and socket updates converge;
5. one observer leaves, resource remains for the other;
6. final observer leaves, grace/disconnect policy applies;
7. reconnect/backoff produces correct state;
8. no duplicate connection for same ResourceKey;
9. different ResourceKeys remain independent.

## 20.9 Refresh tests

Separate two layers.

### Trigger layer

- foreground emits trigger;
- reconnect emits trigger;
- visibility/pull-to-refresh/periodic scheduling;
- concurrency/backpressure/network policy;
- diagnostics.

### Capability layer

- `syncIfDue` skips a key attempted within its minimum interval;
- forced refresh always starts or joins a worker;
- retry/invalidation semantics remain product-owned.

## 20.10 Durable command tests

When outbox is used:

- local transaction is atomic;
- restart retains pending intent;
- retries are idempotent;
- ambiguous response does not double-apply;
- permanent failure is visible/recoverable as product requires.

## 20.11 Qualification suites are permanent tests

Version-sensitive seams need dedicated smoke/regression suites:

- Nav3 back/deep-link/browser/owner destruction;
- keyed smart item retention/cleanup;
- SQLDelight DB creation/migration/query/Web worker path;
- Koin graph/compiler exact toolchain;
- Snapshot runtime cache/offline/live integration;
- observability adapter target support.

A library upgrade is incomplete until its qualification suite passes.

---

# 21. Mechanical architecture enforcement

## 21.1 Convention plugins

Use included `build-logic` with role-specific convention plugins.

Example names:

```text
company.kmp.app
company.kmp.feature
company.kmp.capability-api
company.kmp.capability-impl
company.kmp.ui
company.kmp.foundation-api
company.kmp.foundation-runtime
company.kmp.platform-api
company.kmp.platform-impl
company.kmp.storage
company.kmp.testkit
```

A convention plugin can supply:

- KMP targets/source-set conventions;
- compiler flags;
- common dependencies;
- test defaults;
- explicit API/ABI config;
- module role/subrole used by graph validation;
- for `:foundation:*`, explicit `foundation_api` versus `foundation_runtime` classification;
- standard verification tasks.

## 21.2 Graph validator

`checkModuleGraph` or equivalent should:

- read actual project-to-project dependencies;
- classify modules by convention plugin/path;
- enforce role matrix;
- detect physical cycles;
- normalize Capability API/Impl families for logical cycle detection;
- later support target visibility where justified.

## 21.3 Enforcement strength hierarchy

Use the strongest/earliest mechanism that can express the rule:

1. **No Gradle dependency edge** - code cannot resolve the implementation.
2. **Kotlin/module visibility (`internal`)** - implementation cannot escape its owner module.
3. **Explicit API + ABI review** - public-surface widening is visible.
4. **Convention/graph/compiler/DI validation** - deterministic architecture/construction failure.
5. **Structural/source tests (Konsist or equivalent)** - intra-module semantics not expressible above.
6. **Behavioral/runtime tests** - lifecycle/resource behavior.
7. **Review/documentation** - judgment for facts machines cannot know.

Do not lint an import repeatedly when a convention plugin can simply avoid adding that dependency.

## 21.4 Konsist/source rules

Reserve source rules for constraints the stronger layers cannot fully express:

1. peer Cell implementations inside one Feature do not import/call one another's internals;
2. business/data classes do not implement `KoinComponent` or use service-locator/global `get()` patterns;
3. UiCommand payloads do not carry correctness-bearing domain/resource objects where enforceable;
4. externally reusable Feature presentation symbols are public only under the approved child Feature `.api` package; callers of another Feature import only that package;
5. Capability public APIs expose only approved API packages/types;
6. `foundation_api` modules do not expose/runtime-depend on forbidden lower product/runtime mechanisms in their public surface;
7. intentionally quarantined framework/runtime imports do not leak outside approved packages;
8. no hard-coded `Dispatchers.*` or global coroutine-scope construction in Feature/business code where injected policy applies;
9. every preferences file and secret store name a Capability opens is unique across the app and prefixed with that Capability's name (`StoreNamesRuleTest`);
10. the module list `startKoin` loads is literal module names, one per line, so the Koin compiler plugin keeps validating typed definitions across the whole graph (`KoinApplicationModulesRuleTest`, §18.7).

Graph/visibility rules remain primary for Feature -> Impl, UI -> Koin/Capability, Capability API -> infrastructure, and Capability Impl -> Feature/UI/other Impl.

## 21.5 Explicit API / ABI

Use Kotlin explicit API and ABI checks for stable public modules.

Historically the architecture discussion referenced Kotlin's `binary-compatibility-validator`; use that tool or its maintained successor/equivalent for the project's Kotlin/toolchain version. The architecture requirement is **reviewable public-surface change**, not loyalty to one ABI plugin.

The purpose is not ceremony. It makes public-contract widening visible and reviewable.

## 21.6 Standard verification entry point

Public semantic contract:

```bash
# human/agent inner loop
helix-kmp verify --fast --affected

# repository-wide fast gate
helix-kmp verify --fast

# full supported-target matrix
helix-kmp verify --full
```

If no tier is supplied locally, tooling may default to `--fast`, but must print the chosen tier. CI must name the tier explicitly.

### Fast tier

Typical checks:

```text
JVM/common compilation sufficient for normal Kotlin errors
affected unit/state/resource tests
module graph + logical cycles
source rules
Koin/DI validation where it does not require the whole native matrix
ABI/public API checks for affected stable modules
generator/tooling tests when affected
```

Track p50/p95 duration on the reference developer/agent environment. A practical operational target is p95 around or below two minutes; it is not a timeless Helix law.

### Full tier

Typical checks:

```text
Android target build/tests
iOS/Kotlin-Native compilation/integration
Desktop target
Web/Wasm compilation + browser regressions
Nav3/deep-link/browser qualification
SQLDelight Web Worker qualification when affected
keyed ownership / Compose retain qualification
goldens/accessibility as configured
critical integration/E2E/upgrade suites
```

PR CI always runs the fast gate plus affected platform protection. Main/release/architecture-critical dependency upgrades run the full matrix.

Agents iterate on `--fast --affected`; they do not repeatedly run iOS/Wasm after every edit.

Before the CLI exists, Gradle umbrella tasks may provide the same tier semantics.

## 21.7 Exceptions

Architecture exceptions require:

```text
rule
scope
owner
reason
created date
expiry/review date
removal/revisit condition
```

Untracked blanket suppression is prohibited.

Expired exceptions should fail verification when tooling supports it.

---

# 22. The Helix KMP control plane

## 22.1 Why the control plane is architecture

A large multi-contributor codebase cannot rely on everyone remembering a document.

The control plane makes the correct path easier than invention and makes illegal paths fail quickly.

### 22.1.1 Contract breadth is not day-one implementation breadth

The complete command vocabulary is designed now, but implementation is staged from one canonical file.

The stage table is [`config/helix/control-plane-stages.json`](../../config/helix/control-plane-stages.json): it is the single authority for what each stage gates, requires and makes available, and the `helix-kmp` CLI reads it. `tooling/helix-kmp/STAGE` names the stage this repository is currently at.

Interpretation:

- **P0** is sufficient to build the first real Cell safely.
- **P1** is required before Helix adoption is declared complete.
- **P2** is earned from measured product history.
- root `AGENTS.md` must be phase-aware: it may mention only commands actually available at the current stage.
- Appendix A, now in [`templates/agents-and-skills.md`](templates/agents-and-skills.md), is the **post-adoption/P1-complete** root template, not a P0 bootstrap template.
- until the agent-instruction generator/no-drift checker exists, do not claim that template is generated.

This preserves the intended control plane without violating "complexity is earned." 

```mermaid
flowchart LR
    I[Intent] --> S[Workflow Skill]
    S --> C[helix-kmp context]
    C --> E[Create/Edit]
    E --> V[helix-kmp verify]
    V --> P[helix-kmp impact]
    V -->|failure| D[helix-kmp doctor]
    D --> R[Repair / approved recipe]
    R --> V
    P --> REV[Human/owner review]
    REV --> M[Merge]
```

## 22.2 Stable public CLI

Public command name:

```text
helix-kmp
```

Do not publish a bare global `helix` CLI because that name is crowded and collides with existing software.

Required command family:

```text
helix-kmp graph
helix-kmp context
helix-kmp create
helix-kmp verify
helix-kmp impact
helix-kmp doctor
helix-kmp extract
helix-kmp migrate
helix-kmp gallery
```

Additional operations may be subcommands/recipes rather than proliferating top-level verbs:

- reuse;
- split API/Impl;
- merge;
- resolve cycle;
- repair illegal edge.

## 22.3 Standard CLI flags

Mutating/evolution commands should share semantics:

```text
--dry-run    calculate plan; write nothing
--explain    include rules/evidence/reasoning
--apply      perform deterministic edits
--verify     run required postconditions
--json       machine-readable report
--affected   scope work by dependency/ownership impact
--fast       inner-loop verification tier
--full       full supported-target verification tier
```

Humans, agents, and CI should use the same command contracts.

## 22.4 Command catalog

### `graph`

Purpose:

- derive repository architecture graph;
- show roles/scopes/platforms/owners/dependencies/public surface;
- detect cycles.

### `context`

Purpose:

- produce bounded task context packet for humans/agents.

Example:

```bash
helix-kmp context cricket --json
```

### `create`

Purpose:

- generate standard Feature/Screen/Cell/UI/Capability structures.

Examples:

```bash
helix-kmp create cell cricket live-score --dry-run --explain
helix-kmp create capability bookmarks --apply --verify
```

### `verify`

Purpose:

- execute architecture + test + qualification gates.

### `impact`

Purpose:

- calculate blast radius of a symbol/module/diff.

Report:

- direct/transitive modules;
- tests;
- owners;
- ABI/public consumers;
- qualification suites;
- expected vs surprising impact.

### `doctor`

Purpose:

- diagnose illegal edges and architecture pressure;
- explain rule/evidence;
- recommend approved repair/extraction/merge;
- never silently refactor by default.

Example diagnosis:

```text
Violation: feature:article imports capability:cricket-impl
Rule: Feature may depend on Capability API, never business Impl
Approved repair: use CricketQueries from cricket-api
Mechanical recipe available: yes
```

### `extract`

Purpose:

- perform evidence-approved boundary extraction.

Examples:

```bash
helix-kmp extract ui cricket score --dry-run --explain
helix-kmp extract capability cricket --apply --verify
```

### `migrate`

Purpose:

- apply architecture-kit/convention migration recipes.

```bash
helix-kmp migrate --to architecture-kit-v4 --affected --dry-run
helix-kmp migrate --to architecture-kit-v4 --affected --apply --verify
```

### `gallery`

Purpose:

- build/launch fixture review matrix.

## 22.5 Internal scripts are replaceable

The CLI is stable. Implementation may use:

- Kotlin/JVM;
- Gradle tasks;
- Kotlin PSI/Analysis API;
- OpenRewrite-style recipes;
- Python;
- shell;
- other deterministic tooling.

Recommended internal layout:

```text
tooling/helix-kmp/
  cli/
  graph/
  verify/
  context/
  impact/
  doctor/
  generators/
  recipes/
  qualification/
  gallery/
```

The implementation may be one binary, several Gradle tasks, or a set of helper scripts. If split internally, the responsibilities should stay explicit:

| Internal responsibility | Input | Deterministic output / side effect |
|---|---|---|
| graph extractor | Gradle projects/configuration, role plugins, KMP targets | normalized physical + logical dependency graph |
| source/architecture verifier | graph + Kotlin source | rule violations with stable identifiers and repair guidance |
| ABI/public-surface verifier | API modules + ABI baseline | intentional/unintentional public surface diff |
| context renderer | target + graph + budget/profile | bounded source/API/rule/test/fixture packet, JSON-capable |
| impact calculator | target/symbol/diff + graph | affected modules/tests/owners/ABI/qualification suites |
| doctor analyzer | graph/history/violations/metrics | evidence-ranked extraction/merge/repair recommendations |
| generator | requested role/profile/name | minimal conventional files + Gradle wiring + fixtures/tests as relevant |
| refactoring recipe runner | approved recipe + target | syntax-aware dry-run/apply diff followed by verification |
| qualification runner | dependency/runtime seam | repeatable target matrix and regression evidence |
| gallery indexer/launcher | fixture declarations | reviewable Cell x state matrix |

Internal filenames are intentionally **not architecture facts**. Agents should call the public CLI rather than depending directly on `scripts/foo.py` paths, unless the CLI itself is being developed.

## 22.6 Script requirements

Scripts invoked by agents/CI should be:

- deterministic for the same repository state;
- idempotent where possible;
- safe in dry-run;
- explicit about files changed;
- non-interactive in `--json` mode;
- fixture-testable;
- fail-fast with stable rule IDs;
- actionable in error messages;
- free from hidden duplicated architecture facts.

## 22.7 Graph data model

A graph node can derive:

```text
module path
role
scope
platform targets
owner
stability/qualification when relevant
actual Gradle dependencies
public API/ABI summary
```

The same graph powers:

- dependency validation;
- context packets;
- impact/affected tests;
- ownership routing;
- `doctor`;
- architecture search/index;
- migration planning;
- context metrics.

## 22.8 Context packet contract

Inputs:

```text
target scope/module/symbol
task type (optional)
context budget/profile (optional)
```

Default priority:

1. target implementation and tests in full;
2. direct dependency public APIs;
3. applicable architecture rules;
4. fixtures/acceptance examples;
5. exact verify commands;
6. on-demand links to deeper internals.

A structured packet should be able to expose sections such as:

```text
TASK TARGET
- role / scope / owner / platform

PUBLIC ENTRY POINTS
- Screen / Cell / Capability APIs relevant to the task

GRAPH SLICE
- direct dependencies and reverse dependencies relevant to the expected change

RULES
- applicable architecture rules
- protected boundaries
- relevant ADR Revisit-when conditions

SOURCE
- target implementation and tests in full

DEPENDENCY CONTEXT
- public APIs of direct dependencies, not their unrelated internals

ACCEPTANCE
- fixtures, expected states, scenario/E2E references where relevant

VERIFY
- exact commands and expected evidence
```

```mermaid
flowchart LR
    T[Task target] --> G[Architecture graph]
    G --> SRC[Target source + tests]
    G --> API[Direct dependency APIs]
    G --> R[Applicable rules / ADRs]
    G --> A[Fixtures / acceptance]
    G --> V[Verify commands]
    SRC --> P[Bounded context packet]
    API --> P
    R --> P
    A --> P
    V --> P
    P --> AG[Human or coding agent]
```

Do not automatically load:

- unrelated sibling implementations;
- all transitive data internals;
- full repository docs;
- distant platform code.

## 22.9 Context budget and investment policy

Context generation can decay in value as model/tooling quality improves, so it is lower implementation priority than deterministic verification and actionable repair messages.

Start with a cheap packet:

```text
target source/tests
direct public dependency APIs
applicable rule IDs
exact fast verify command
```

Add sophisticated ranking/token budgeting only when measured agent outcomes justify it.

Historical heuristic: keep a working set comfortably below the smallest production agent context, for example around a configurable fraction of the window.

Final rule:

- no universal token threshold is a Helix law;
- measure real task success/cost;
- use context pressure as one `doctor` signal;
- do not physically split code solely to satisfy a temporary model limit when cohesion would suffer.

## 22.10 Root `AGENTS.md`

Keep root agent instructions concise and high-value.

There is **one post-adoption/P1-complete copyable root template, Appendix A in [`templates/agents-and-skills.md`](templates/agents-and-skills.md)**. Do not install it during P0 unchanged, because P1 commands may not exist yet.

The P1 agent-instruction generator/no-drift checker must read the canonical stage file `config/helix/control-plane-stages.json` and omit unavailable commands for earlier phases.

Generation/check inputs:

- laws: Section 2;
- vocabulary: Section 7;
- dependency prohibitions/rule IDs: Section 9 policy;
- identity: Section 12;
- verification/workflow: Sections 21-22;
- protected work: [Section 31, now in `../guides/contributing-without-android-expertise.md`](../guides/contributing-without-android-expertise.md#314-protected-areas).

Repository `AGENTS.md` should be generated or checked against these inputs.

## 22.11 Nested agent instructions

Nested `AGENTS.md`/equivalent files are optional **deltas**, not mandatory per-Cell ceremony.

Use only when a path has important local constraints that cannot be generated or inferred.

Avoid stale duplicated architecture manuals in every folder.

## 22.12 Workflow-oriented AI Skills

Use a few workflow Skills, not one Skill per CLI command.

Canonical family:

```text
build-feature
evolve-architecture
debug-and-repair
review-and-verify
```

Shared rules:

- call stable `helix-kmp`, not private script paths;
- use `verify --fast --affected` for the normal edit loop;
- use impact/doctor evidence before boundary movement;
- never bypass protected review;
- return deterministic evidence;
- use the same permissions as humans;
- do not copy the whole architecture into every Skill.

**Appendix B, now in [`templates/agents-and-skills.md`](templates/agents-and-skills.md), is the single copyable Skill-template source.**

### 22.12.1 Skill bundle structure

A workflow Skill may contain:

```text
SKILL.md       workflow, stop/escalate conditions, evidence contract
scripts/       deterministic helpers only when genuinely reusable
references/    concise tool/domain references loaded on demand
assets/        templates/fixtures when required by the workflow
```

Do **not** copy this entire master into every Skill. The Skill teaches the workflow and invokes `helix-kmp context` for current repository facts.

The initial package provides four Skill templates, but Skills are adapters around Helix workflows rather than the source of architecture truth.

### 22.12.2 Agent/vendor portability

Helix must not depend on one coding-agent vendor. The portable contract is:

```text
repository + Gradle graph + source
        + AGENTS.md/local deltas
        + helix-kmp CLI/JSON
        + deterministic verification
        + workflow semantics
```

An OpenAI Skill, another agent platform's workflow file, an IDE action, or a human runbook may adapt that contract without changing Helix architecture facts.

Agents receive **no special architecture permissions**: they use the same generators, verification, exception process, and ownership rules as human developers and CI.

## 22.13 Agent evidence contract

An AI-generated change should return evidence rather than "done."

At minimum where applicable:

```text
Target/owner changed
Architecture classification used
Files/modules changed
Verification command + result
Tests added/updated
Fixture/golden evidence
Impact report
Public API/ABI changes
Qualification suites touched
Exceptions/risks
```

## 22.14 Safe contribution lanes

| Lane | Typical contribution | Review expectation |
|---|---|---|
| Declarative | copy, content order, flags, design tokens, constrained schemas | normal product review |
| Acceptance | fixtures, examples, edge cases, expected screenshots/business rules | QA/product/design review |
| Bounded code | one scope/Cell/UI/Capability consumer through Skill + verify | code owner |
| Restricted | auth, payments, privacy, migrations, crypto, shared concurrency, public APIs, architecture | experienced specialist/owner |

Goal: ordinary contributions should not require Android lifecycle knowledge when the task does not cross lifecycle/platform boundaries.

---

# 23. Architecture evolution and `doctor`

## 23.1 Architecture is a graph, not a folder tree

Folders are a view. The graph is the operational model.

```mermaid
flowchart TD
    SRC[Gradle + Kotlin + CODEOWNERS + target config] --> G[Helix architecture graph]
    G --> V[Verify]
    G --> C[Context]
    G --> I[Impact]
    G --> D[Doctor]
    G --> O[Ownership routing]
    G --> M[Migration planning]
    G --> X[Context/build metrics]
```

## 23.2 Objective extraction signals

`doctor` can consider:

- second independent consumer;
- second owning team;
- incoming dependencies;
- public API size/churn;
- ABI fanout;
- compile/test fanout;
- context surface;
- repeated exceptions/illegal-edge attempts;
- Git co-change;
- duplicated reuse;
- database/schema pressure.

It should show evidence and confidence.

Example:

```text
CRICKET
context surface             41,200 tokens
external capability users   4
external pure UI users      3
compile fanout              high
feature/data co-change      8%

Recommendation
- extract capability: HIGH confidence
- extract score UI: HIGH confidence
- keep Screen + Cell presentation together
```

Numbers are illustrative; thresholds are organization-configurable.

## 23.3 `doctor` recommends, humans approve

`doctor` does not silently restructure architecture.

Automated change is appropriate when:

- evidence supports the boundary move;
- a reviewed deterministic recipe exists;
- human architecture/owner approval is obtained for consequential boundary changes.

## 23.4 Architecture-kit versioning

A convention change should ship as a coordinated architecture-kit release:

```text
new generator
+ new verification rule
+ codemod recipe
+ master/derived docs update
+ qualification evidence
= architecture-kit release
```

Then:

```bash
helix-kmp migrate --to architecture-kit-v4 --affected --apply --verify
```

Prefer centrally derivable kit version or minimal metadata rather than noisy version markers in every file.

## 23.5 Refactoring recipes

Core recipe families:

```text
extract capability
extract UI
split API/Impl when justified
merge over-fragmented boundary
resolve cycle through stable API/ownership move
migrate architecture-kit version
```

Prefer syntax-aware Kotlin PSI/Analysis/OpenRewrite-style tooling over regex for semantic refactors.

## 23.6 Verification is a postcondition

A codemod is successful only when:

```text
new graph valid
public API diff intentional
required tests pass
fixtures/goldens valid or reviewed
qualification suites pass when affected
obsolete adapter/old boundary removed when no longer needed
```

## 23.7 Exception pressure is architecture evidence

Repeated exceptions against the same rule/scope may mean:

- bad implementation habits;
- missing generator support;
- a boundary that no longer fits reality.

`doctor` should distinguish these where possible.

---

# 24. ADR and qualification governance

## 24.1 ADR template

Every consequential decision records:

```text
Decision
Context / problem
Why
Alternatives considered
Consequences / costs
Qualification evidence
Revisit when
Migration/escape path
```

## 24.2 `Revisit when` is mandatory for consequential bets

Architecture decisions are conditional bets, not scripture.

A useful review question is:

> **Has any ADR revisit condition become true?**

## 24.3 Qualification states

Use a small vocabulary:

```text
qualified
qualified-with-warning
accepted-experimental
unsupported
```

Optional additional state:

```text
under-evaluation
```

Do not let agents treat `accepted-experimental` as equivalent to stable.

## 24.4 Dependency upgrades are architecture-adjacent changes

Architecture-critical library upgrades require:

- version pin change;
- qualification suite;
- impact check;
- updated status/evidence;
- ADR revisit review if behavior changed materially.

## 24.5 Framework watch

Recommended operational practice: maintain a scheduled architecture-framework watch for meaningful changes in:

- Navigation 3;
- Compose Multiplatform;
- Decompose;
- Circuit;
- third-party snapshot/resource libraries (Store5 and successors) against the owned runtime;
- SQLDelight;
- Koin/Koin compiler;
- observability/KMP tooling;
- relevant architecture/codemod tooling.

Notify only when a change is substantial enough to affect ranking, qualification, or a recorded ADR condition. Include impact and a POC/migration recommendation rather than raw release notes.

### Historical operating note

During the architecture-design discussion, a **daily 09:00 condition-watch automation** named `Architecture Framework Watch` was created for this purpose. Its intent was to check the frameworks above and notify only when a development was substantial enough to reconsider the ranking/baseline, with the expected impact and a POC or migration recommendation.

The exact schedule and existence of that automation are **not Helix architecture requirements**. They are recorded only as provenance for the framework-watch practice; each team may choose an appropriate cadence/tool.

---

# 25. Current ADR catalog

Moved to [docs/architecture/decisions.md](decisions.md). The master remains the normative source; this section holds no rules.

---

# 26. Qualification snapshot and evidence ledger

This section separates project POC evidence from public upstream release facts.

## 26.1 Project POC outcomes

| Area | Internal observed result | Current decision |
|---|---|---|
| keyed smart-item ownership | offscreen composition disposal retained ViewModel/state; logical removal destroyed owner immediately | adopt one narrow reusable keyed owner |
| Navigation 3 | required back stack, deep-link/URL integration under project POC, entry-scoped VM destruction passed | selected Screen runtime |
| Decompose | viable broader component runtime | archive as evaluated alternative; do not mix by default |
| SQLDelight Web/Wasm | 2.2.1 actual web-worker path passed headless-browser qualification | approved; rerun on upgrades |
| Koin compiler | application graph verification passed; earlier exact Kotlin combo produced compatibility warning | keep with qualification warning until exact-upgrade suite passes |
| observability | semantic FeatureObserver worked with Kermit and optional OTel adapter; required Wasm OTel path absent in selected POC | Kermit default; adapter-neutral |
| historical Store5 Snapshot POC | cache-first, refresh, offline, SQLDelight SoT passed | superseded 2026-09-04 by the owned runtime after the value comparison fired |
| owned sync coordinator | per-key joining, due-checking, observer counting, status ledger, eviction, caller cancellation, reconnect, and SQLDelight integration passed | current narrow project-owned runtime; the value-tracking `SnapshotResource` predecessor was superseded 2026-09-05 (ADR-43) |
| Snapshot/live integration | REST + SQLDelight + live updates with two observers passed | live keyed coordinator + shared resource; the sync coordinator is not the UI lifecycle layer |
| durable mutation | local-first bookmark + offline retry passed | explicit Commands; outbox only when required |
| identity | separate FeatureInstanceKey/ResourceKey behavior validated | retain three-identity model |

## 26.2 Upstream verification policy and source ledger

Fast-moving upstream "latest version" numbers are **not master-source truth**.

Operational authority:

```text
exact dependency pin          -> repository version catalog / lockfiles
project qualification status  -> qualification test evidence
upstream capability/source     -> URLs in Section 38 (background.md)
architecture decision          -> this master
```

The master may retain historical POC versions where they explain evidence, but it must not transcribe "current latest stable/beta/alpha" as if that were durable architecture.

### Navigation 3 qualification source

Use the repository pin as the exact version authority. The project POC qualified the required Nav3 ownership/back-stack behavior. Upgrades rerun the Nav3 + browser/direct/deep-link suite.

### Compose Multiplatform Navigation 3

Upstream KMP documentation is the source for supported targets/browser integration. The repository pin plus permanent browser-history regression suite determine project qualification.

### Compose Runtime `retain` / retained values

Upstream Compose Runtime documentation confirms the retain/retained-values APIs exist, but API existence is not enough to replace the keyed Cell owner.

Immediate project qualification on the **repository-pinned** Compose Multiplatform/runtime version:

```text
Android + iOS + Desktop + Web/Wasm
  1. compose keyed Cell
  2. mutate presentation state/ViewModel
  3. leave composition due to scrolling while logical item remains
  4. return and prove the same presentation owner/state
  5. logically remove item and prove immediate retirement
  6. re-add as new placement and prove no state leak
  7. reorder
  8. host the same ResourceKey in two distinct placements
```

Decision:

- lifecycle 2.11.0 `ViewModelStoreProvider` satisfies the keyed store/ref-count contract and now
  backs the reduced `StatefulLazyItem` helper;
- Compose `retain` remains a separate candidate only if it can delete more infrastructure while
  preserving the exact four-target suite;
- minimize private keyed-runtime coupling around the first-party provider.

### SQLDelight

The repository pin is authoritative. The historical project POC qualified the asynchronous Web Worker path; upgrades rerun that target-specific suite.

### Sync coordinator runtime

The owned coordinator is qualified by permanent state-table, cancellation, eviction, observer,
reconnect, and Capability integration tests. The historical Store5 pin was removed when ADR-05 was
superseded on 2026-09-04; the value-tracking `SnapshotResource` was removed under ADR-43 on
2026-09-05.

### Koin / compiler validation

The repository's Kotlin + Koin + compiler-plugin pins are authoritative. Upstream docs/source are evidence only. Rerun the exact KMP graph/build/compiler suite before changing qualification.

For canonical ViewModel snippets, verify against the source/docs for the pinned Koin Compose ViewModel artifact. The reviewed common source exposes `parameters` as `ParametersDefinition?`, hence the lambda form used in Section 30.4.

### OpenTelemetry Kotlin

Do not transcribe upstream "current" versions here. Project support remains adapter/target-qualified; Features depend only on semantic observability seams.

## 26.3 Current qualification register

| Technology/mechanism | Status | Note |
|---|---|---|
| Navigation 3 | qualified | exact repository pin from version catalog; project runtime behavior qualified |
| Nav3 CMP browser history | qualified-with-warning | exact repository pin + permanent Back/Forward/URL/direct/refresh/deep-link suite |
| keyed `StatefulLazyItem` owner | qualified | backed by lifecycle 2.11.0 `ViewModelStoreProvider`; `rememberViewportKeys` supplies Decompose `ChildItems`-style destroy-beyond-buffer |
| Compose `retain` / RetainedValuesStore replacement | under-evaluation | evaluate exact repository pin on four-target lazy/keyed/logical-removal suite |
| SQLDelight | qualified | exact repository pin + required-target/Web Worker regression |
| owned Snapshot resource runtime | qualified | per-key ledger and SQLDelight integration covered by permanent regression tests |
| Koin compiler/graph validation | qualified-with-warning | exact repository Kotlin/Koin/compiler pins must remain regression-qualified |
| Kermit | selected default adapter | broad neutral logging path |
| OpenTelemetry Kotlin | optional/target-dependent | adapter only; never Feature contract |
| Circuit | evaluated alternative | no default adoption |
| Decompose | evaluated alternative | escape hatch for general component-tree topology |
| GraphQL/Apollo | deferred/not currently applicable | backend not expected to support near term |

---

# 27. Known costs, risks, and honest counterpoints

## 27.1 Upfront platform/control-plane cost

Helix invests in:

- build logic;
- architecture tests;
- generators;
- CLI;
- Skills/context;
- runtime diagnostics;
- qualification suites;
- codemods.

This is real work. The rationale is to pay repeated architecture decision/debug/migration cost once at the platform seam rather than in each Feature.

## 27.2 Bespoke vocabulary/tooling cost

Every contributor must learn:

- Feature;
- Screen;
- Cell;
- Capability;
- Resource;
- three identities;
- a private CLI.

Mitigation:

- keep vocabulary small;
- use standard platform primitives under it;
- generate workflows;
- write audience-specific concise docs;
- avoid thematic taxonomy growth.

## 27.3 AI capability may reduce context pressure

Mitigation:

- context optimization is not a law;
- low blast radius/ownership remain independent benefits;
- simplify context tooling if its value decays.

## 27.4 Generator does not remove reading cost

Mitigation:

- no fixed file ceremony;
- measure real feature readability;
- merge files/boundaries when cohesion improves.

## 27.5 Single DB can become a coordination hotspot

Mitigation:

- logical capability ownership;
- physical topology is reversible;
- measure migration/invalidation/co-change pressure.

## 27.6 Sync coordinator is owned infrastructure

Mitigation:

- narrow and hidden behind Capability Impl;
- domain-blind: coordinates work and status, never values;
- process-bounded per-key ledger with eviction;
- permanent state-table, cancellation, eviction, observer, and reconnect qualification suite;
- replacement remains possible without changing consumers.

## 27.7 Custom keyed owner is owned runtime infrastructure

Mitigation:

- narrow scope;
- strong regression tests;
- delete it if first-party primitive matches semantics;
- reconsider Decompose if it grows into a component runtime.

## 27.8 Web/Wasm ecosystem maturity

Mitigation:

- explicit qualification suites;
- vendor-neutral boundaries;
- revisit Compose Web if product requirements diverge.

## 27.9 Golden-test rot

Mitigation:

- goldens are narrow visual evidence only;
- separate framework churn;
- fixtures remain useful even if the golden tool changes.

## 27.10 Verification latency can dominate agent/human loops

Mitigation:

- fast/full tiers;
- affected slicing;
- measure p50/p95 fast verification;
- optimize rule/DI/unit checks for inner-loop latency;
- reserve full matrix for CI/release/qualification.

## 27.11 Bespoke primitives can become hidden framework debt

Mitigation:

- Standard Primitive First;
- complete Cricket/reference usage;
- stable diagnostics;
- regression suite;
- explicit delete/revisit condition;
- qualify Compose `retain` and other first-party replacements before private coupling expands.

## 27.12 Documentation can become stale

Agents may trust repository instructions too strongly.

Mitigation:

- master source owns rationale;
- graph/source code owns operational facts;
- generated docs/context where possible;
- root instructions concise;
- nested deltas only when necessary;
- architecture-kit migrations update tools/docs together.

---

# 28. Naming and public identity

## 28.1 Final naming stack

```text
Architecture:
    Helix KMP

Descriptor:
    a modular ownership architecture for Kotlin Multiplatform

Optional descriptor:
    a modular ownership architecture for AI-first Kotlin Multiplatform applications

Frozen spec:
    Helix KMP Baseline v1

Internal shorthand:
    Helix

Compliance:
    Helix-compliant

Public CLI:
    helix-kmp
```

## 28.2 Public naming rule

> Use **Helix KMP** for public-facing names, documentation, repositories, artifacts, plugins, and tooling. **Helix** may be conversational/internal shorthand but should not be published as an unqualified global software brand or CLI.

Use organization-qualified package/artifact namespaces.

## 28.3 Why Helix KMP was chosen

Semantically, Helix represented:

- intertwined Application Plane + Control Plane;
- ownership plus evolution;
- stable structural rails with replaceable implementation;
- continuous architecture adaptation rather than a static folder taxonomy.

It represented the design more strongly than neutral arrangement-only names.

## 28.4 Public collision screening

Pre-clearance web screening found material uses of the unqualified name **Helix** in software. The most relevant signals included:

- **Sitecore Helix**, an established modular software architecture whose public vocabulary includes Project / Feature / Foundation; this was the most material architecture-name collision;
- an active **Helix Framework** in the Spring/Gradle ecosystem with its own Helix CLI;
- **Apache Helix** artifacts such as `org.apache.helix` / `helix-core`;
- other Helix developer tooling such as HelixML and multiple unrelated GitHub projects;
- multiple global `helix` command-line tools already using verbs such as `create`, `doctor`, `init`, or `run`;
- package-name crowding in Maven/JVM, PyPI, npm, and generic software domains;
- software-related HELIX trademark/brand signals including the Perforce HELIX family, Applied Intuition HELIX, BMC HELIX, and related records including India-facing signals.

This is why the final public CLI is **`helix-kmp`**, not bare `helix`, and why public artifacts/packages should use organization-qualified namespaces.

Exact searches for forms such as:

```text
"Helix KMP"
helix-kmp
helixkmp
"Helix KMP Architecture"
```

did not surface an established exact architecture/framework/package identity during the original screening. That was encouraging but is **pre-clearance only**, not legal/trademark clearance.

If this becomes a public/commercial brand, formal trademark/domain/package clearance is still required.

## 28.5 Names explored

Historical candidates included:

- **Axifold** - relatively clean but meaning felt opaque;
- **Invarail** - architecture-friendly semantics but difficult pronunciation;
- **Boundrail** - descriptive but unexciting;
- **Vinyas** - viable and more publicly ownable; the word was understood as arrangement/design/structure. Screening found no major established software-architecture collision, but did find smaller design/engineering uses, `vinyas.dev`, and an old India Class 9 VINYAS software/design-systems application reported as abandoned;
- **Helix KMP** - strongest architecture representation, chosen with a qualified public name.

In the qualitative comparison, Helix was judged to represent the architecture more strongly (roughly **9.5/10** for semantic representation versus **8.5/10** for Vinyas). Those were discussion heuristics, not trademark/market scores. Vinyas was cleaner for ownability; Helix better expressed the intertwined **Application Plane + Control Plane** and **Enforced Evolution** idea.

Other names were eliminated due active collisions or weaker fit, including examples such as Tessera, Trellis, Keel, Axiom, Niyam, Sutura, Modara, Modulyn, Invarix, Ordin/Ordinex, Scopeward, Stateward, RuleMesh, Ownara, Structara, Boundset, Ownbound, Propria, Samanvay, and Sutra.

---

# 29. One-time adoption model for the existing KMP starter

Moved to [docs/history/helix-adoption-model.md](../history/helix-adoption-model.md). The master remains the normative source; this section holds no rules.

---

# 30. Canonical Cricket reference slice

Moved to [docs/architecture/reference-slice.md](reference-slice.md), except section 30.4 below,
which is normative and is retained here. The master remains the normative source; the rest of
this section holds no rules.

## 30.4 Canonical Cell + ViewModel acquisition

Koin Compose is allowed at a **Feature Screen/Cell composition entry** to acquire that presentation owner's ViewModel. It remains forbidden in pure `:ui:*`.

The keyed host installs the keyed `ViewModelStoreOwner` for its subtree; `koinViewModel()` resolves against that current owner.

```kotlin
@Composable
fun LiveScoreCell(
    matchId: MatchId,
    instanceKey: FeatureInstanceKey,
    onOutput: (LiveScoreOutput) -> Unit,
) {
    val viewModel: LiveScoreViewModel = koinViewModel(
        key = instanceKey.value,
        parameters = { parametersOf(matchId, instanceKey) },
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOutput by rememberUpdatedState(onOutput)
    val lifecycleOwner = LocalLifecycleOwner.current

    LiveScoreUi(
        state = state,
        onAction = viewModel::onAction,
    )

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.outputs.collect { output ->
                currentOnOutput(output)
            }
        }
    }
}
```

Why these details are canonical:

- current Koin Compose ViewModel resolution accepts a `ParametersDefinition`, so the parameter expression is a lambda;
- `collectAsStateWithLifecycle()` is available in common lifecycle-compose APIs and stops unnecessary StateFlow collection when the presentation lifecycle is inactive;
- `rememberUpdatedState` prevents callback identity churn from restarting the Output collector;
- the effect is keyed to the ViewModel/lifecycle owner, not to an unstable bound callback reference;
- a Cell fills width and wraps height; it never uses `fillMaxSize()` or owns vertical scrolling. The host supplies scrolling through `modifier` and insets through `contentPadding`, applied inside the Cell so content can scroll under translucent bars;
- surface-level element state such as `SnackbarHostState` is hoisted as a nullable parameter with a self-hosting default;
- the Cell checks that the resolved ViewModel's id equals the `id` parameter and fails fast otherwise: the ViewModel is keyed by `instanceKey` alone, so a host that reuses one placement key for a different id would otherwise keep rendering the stale instance.

Koin definition:

```kotlin
viewModel { params ->
    val matchId: MatchId = params.get()
    val instanceKey: FeatureInstanceKey = params.get()

    LiveScoreViewModel(
        matchId = matchId,
        instanceKey = instanceKey,
        queries = get(),
        followTeam = get(),
        trace = get<FeatureTraceFactory>().create(
            feature = "cricket/live-score",
            instanceKey = instanceKey,
        ),
    )
}
```

The ViewModel maps synchronized resource observations into product-specific State.

A minimal reference State/mapping is intentionally shown so agents do not invent incompatible loading/offline semantics:

```kotlin
data class LiveScoreState(
    val score: LiveScore? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val problem: LiveScoreProblemUi? = null,
) {
    companion object {
        val initial = LiveScoreState(isInitialLoading = true)
    }
}

enum class LiveScoreProblemUi {
    OFFLINE,
    ACCESS,
    RETRYABLE,
    FAILED,
}

private fun ResourceObservation<LiveScore>.toLiveScoreState(): LiveScoreState {
    val failure = operation as? ResourceOperation.Failed

    return LiveScoreState(
        score = value,
        isInitialLoading = isInitialLoading,
        isRefreshing = value != null && operation is ResourceOperation.Refreshing,
        problem = failure?.problem?.let { problem ->
            when {
                problem.kind == ProblemKind.OFFLINE ->
                    LiveScoreProblemUi.OFFLINE
                problem.kind == ProblemKind.FORBIDDEN ->
                    LiveScoreProblemUi.ACCESS
                problem.retryable ->
                    LiveScoreProblemUi.RETRYABLE
                else ->
                    LiveScoreProblemUi.FAILED
            }
        },
    )
}
```

The resource envelope is generic; `LiveScoreState` remains product/presentation-specific.

```kotlin
class LiveScoreViewModel(
    matchId: MatchId,
    private val instanceKey: FeatureInstanceKey,
    private val queries: CricketQueries,
    private val followTeam: FollowTeam,
    private val trace: FeatureTrace,
) : ViewModel() {

    val state: StateFlow<LiveScoreState> =
        queries.liveScore(matchId)
            .map { it.toLiveScoreState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LiveScoreState.initial,
            )

    private val _outputs = MutableSharedFlow<LiveScoreOutput>()
    val outputs: SharedFlow<LiveScoreOutput> = _outputs.asSharedFlow()

    fun onAction(action: LiveScoreAction) {
        trace.action(action::class.simpleName ?: "unknown")
        // auth/product guards, commands, local presentation changes, output
    }

    override fun onCleared() {
        trace.close()
    }
}
```

Rules demonstrated by this reference:

- `matchId` selects the Resource; `FeatureInstanceKey` identifies the presentation instance.
- Koin resolution is contained at the Feature composition/DI boundary.
- business/data classes use constructor injection and do not call global Koin access.
- `LiveScoreUi(state, onAction)` is pure rendering and independently testable.
- Output is semantic owner communication while this presentation is active; resource/business correctness does not depend on Output delivery.
- generator output must provide an equally complete executable reference for any new bespoke primitive it introduces.

---

# 31. Contribution without Android expertise

Moved to [docs/guides/contributing-without-android-expertise.md](../guides/contributing-without-android-expertise.md). The master remains the normative source; this section holds no rules.

---

# 32. QA operating model

Moved to [docs/guides/qa-operating-model.md](../guides/qa-operating-model.md). The master remains the normative source; this section holds no rules.

---

# 33. Management outcomes and metrics

Moved to [docs/architecture/background.md](background.md). The master remains the normative source; this section holds no rules.

---

# 34. Documentation derivation rules

Moved to [docs/guides/documentation-derivation.md](../guides/documentation-derivation.md). The master remains the normative source; this section holds no rules.

---

# 35. Architecture red flags

Treat these as likely signs of ownership/boundary drift.

```text
Screen ViewModel opens/owns WebSocket for shared resource
Feature imports capability-impl
Capability impl imports another business impl
UI module resolves Koin ViewModel
UI module imports navigation
Cell imports another Cell implementation
Cell has no stable FeatureInstanceKey despite multiple instances
Resource identity is a LazyList index
RouteKey reused as ResourceKey
UiCommand carries full domain object/resource snapshot
Snapshot runtime/SQLDelight types appear in Feature API
HomeRepository aggregates unrelated domains only for one screen
Global RefreshManager owns Cricket/Article-specific TTL rules
Global EventBus used to avoid dependency design
Per-module YAML duplicates Gradle/role/owner facts
Module-per-Cell generated regardless of need
Generic BaseViewModel forces unused concepts
One physical DB treated as permanent business boundary
Goldens updated to make a behavioral test failure disappear
Architecture rule disabled because generator/tooling lacks repair path
Nested AGENTS/CLAUDE file copied into every Cell with stale duplicated rules
```

---

# 36. Non-goals

Helix does not aim to:

- give every Composable a ViewModel/Cell/module;
- create feature/domain/data/API/impl modules for every idea on day one;
- use one global MVI store;
- use one global EventBus;
- make all screens registry/SDUI-driven;
- force a universal Loading/Content/Error/Hidden state;
- require Circuit/Decompose/custom presenter runtime;
- require GraphQL;
- expose database/vendor types to presentation;
- predict every future extraction boundary;
- replace product/architecture judgment with automatic splitting;
- optimize only for today's AI context window;
- create a private biological taxonomy;
- make DOCX/PDF the live source of truth.

---

# 37. External architecture and ecosystem influences explored

Moved to [docs/architecture/background.md](background.md). The master remains the normative source; this section holds no rules.

---

# 38. Public reference URLs used for qualification/reasoning

Moved to [docs/architecture/background.md](background.md). The master remains the normative source; this section holds no rules.

---

# 39. Final compact mental model

```text
PUBLIC ARCHITECTURE
-------------------
Helix KMP
  four laws:
    Explicit Ownership
    Product-Facing Boundaries
    Independent Lifetimes
    Enforced Evolution

PRESENTATION
------------
Feature
  owns Screens + Cells

Screen
  navigable owner

Cell
  independently hostable stateful presentation
  NOT a base class
  NOT a Gradle module

UI
  immutable model + callbacks
  stateless reusable rendering

PRESENTATION CONTRACT
---------------------
State       mandatory
Action      mandatory for stateful owner
UiCommand   optional transient local UI instruction
Output      optional semantic message to owner

IDENTITY
--------
RouteKey          navigation
FeatureInstanceKey presentation instance
ResourceKey       shared business resource

BUSINESS / DATA
---------------
Capability API
  grouped Queries
  intent Commands
  stable models

Capability Impl
  Snapshot resource runtime / repository / REST / SQLDelight / WebSocket
  refresh/resource semantics

Resources
  Snapshot
  Live
  Projection

SYNCHRONIZED READ CONTRACT
--------------------------
ResourceObservation<T>
  value + operation
  mapped by ViewModel into product-specific State

RUNTIME SCOPE
-------------
App runtime parent scope
  -> Capability-owned child jobs
  -> never ViewModel scope for shared resources
  -> never GlobalScope

SESSION / AUTH
--------------
Identity owns session + refresh + logout
Foundation network owns generic bearer/401 mechanics

REFRESH
-------
Common layer:
  external triggers + generic QoS/backpressure

Capability:
  freshness + retry + invalidation + forced/needed semantics

VERIFICATION
------------
verify --fast --affected   human/agent inner loop
verify --full              target-complete CI/release/qualification

CONTROL PLANE
-------------
helix-kmp
  graph
  context
  create
  verify
  impact
  doctor
  extract
  migrate
  gallery

AI Skills
  build-feature
  evolve-architecture
  debug-and-repair
  review-and-verify

ONE FACT -> ONE SOURCE
----------------------
role          convention plugin/path
deps          Gradle graph
scope         naming/rare central override
owner         CODEOWNERS
public API    Kotlin + ABI
qualification central register
exceptions    expiring registry
index/context generated graph output
```

---

# 40. Master maintenance checklist

Before publishing a new master edition:

```text
[ ] Four laws unchanged or explicitly ADR-updated
[ ] Vocabulary has no accidental duplicate/competing terms
[ ] Cell still clearly not a base class/module
[ ] Canonical machine-readable dependency policy file is still the only copy of the role matrix, and the build validator and generated docs read it
[ ] Enforcement uses strongest practical mechanism before structural tests
[ ] Query/Command/ResourceObservation rules match generators/reference slice
[ ] FeatureInstanceKey construction matches keyed-host/registry code
[ ] Runtime identity + application/capability coroutine scope semantics match regressions
[ ] Auth/session/network ownership matches Identity + Foundation implementations
[ ] Refresh QoS/freshness ownership remains split correctly
[ ] Qualification table reflects exact project POCs
[ ] Exact dependency pins are read from repository operational sources, not duplicated as "latest upstream" prose
[ ] Upstream capability/source URLs were rechecked for qualification-sensitive changes
[ ] The P0/P1/P2 stage file is the only staging authority and agent instructions match implemented command availability
[ ] ADR Revisit when conditions reviewed
[ ] CLI command/flag contract matches tooling, including fast/full verification tiers
[ ] AI Skills/root agent template match current workflow
[ ] Context packet contract matches graph tooling and has not outrun measured value
[ ] Doctor/extraction/migration recipes match architecture-kit
[ ] Management metrics include verification latency and contribution lanes reflect operating model
[ ] Naming/public brand rule unchanged or legally re-cleared
[ ] Derived documentation regenerated from this master
[ ] HTML site rebuilt and local links/search validated
```

---

# 41. Closing architecture statement

Helix KMP is not a collection of preferred libraries and it is not a Cell framework.

Its durable claim is:

> **Keep product behavior cohesive, keep unrelated areas loosely coupled, give every state/resource/lifetime an explicit owner, expose stable product-facing boundaries, and automate the creation, verification, diagnosis, and evolution of those boundaries so ordinary changes remain small and understandable for humans and AI agents.**

The architecture should be willing to simplify itself when evidence shows a mechanism no longer pays for its cost. The four laws and the boundary test are more durable than any current framework, module count, AI context size, or persistence technology.

---

# Appendix A. Full root AGENTS.md template

Moved to [docs/architecture/templates/agents-and-skills.md](templates/agents-and-skills.md). The master remains the normative source; this section holds no rules.

---

# Appendix B. Full workflow Skill templates

Moved to [docs/architecture/templates/agents-and-skills.md](templates/agents-and-skills.md). The master remains the normative source; this section holds no rules.

---

# Appendix C. Detailed public CLI workflow examples

## C.1 Add a Cell

```bash
helix-kmp context cricket --json
helix-kmp create cell cricket live-score --dry-run --explain
helix-kmp create cell cricket live-score --apply --verify
helix-kmp impact cricket/live-score
```

## C.2 Diagnose an illegal edge

```bash
helix-kmp verify --fast --affected --json
helix-kmp doctor article --explain
```

## C.3 Extract reusable UI

```bash
helix-kmp extract ui cricket score --dry-run --explain
helix-kmp extract ui cricket score --apply --verify
```

Recipe moves:

- immutable rendering model;
- stateless Composable;
- visual resources.

Recipe must not move:

- ViewModel;
- navigation;
- Koin;
- Capability/resource logic.

## C.4 Extract Capability

```bash
helix-kmp doctor cricket --explain --json
helix-kmp extract capability cricket --dry-run --explain
helix-kmp extract capability cricket --apply --verify
```

## C.5 Architecture-kit migration

```bash
helix-kmp migrate --to architecture-kit-v4 --affected --dry-run --explain
helix-kmp migrate --to architecture-kit-v4 --affected --apply --verify
```

## C.6 Review

```bash
helix-kmp impact --diff origin/main...HEAD --json
helix-kmp verify --fast --affected
helix-kmp gallery
```

---

# Appendix D. Decision matrix: why this and why not that

| Area | Current/final choice | Why | Why not main alternative now | Revisit when |
|---|---|---|---|---|
| architecture unit | Feature + Screen + logical Cell | matches ownership/reuse without module explosion | Screen-only ownership recreates God ViewModel; module-per-Cell too costly | Cells rarely autonomous or ceremony dominates |
| global state model | specialized owners by lifetime/identity | prevents one accidental common owner | universal Redux/MVI store raises coupling and still needs resource/runtime ownership | product state truly converges on one lifetime/identity model |
| presentation owner | ViewModel + UDF | familiar and qualified | Circuit/Molecule adds runtime commitment | measured ceremony dominates useful logic |
| navigation | Navigation 3 | required routing/lifecycle passed, smaller runtime | Decompose broader than needed | nested component-tree topology or helper growth |
| smart embedded lifetime | narrow keyed owner | solves exact lazy Cell gap | full component runtime unnecessary today | first-party replacement or recurring bugs |
| read contracts | grouped Capability Queries | avoids use-case-class explosion | one `ObserveX` class per trivial read too verbose | grouped API becomes unstable/broad |
| writes | intent Commands | semantic and testable | generic CRUD loses product intent | real domain semantics demand different contract |
| Snapshot resource | owned `SyncCoordinator` with a database-owned value | per-key joining, due-checking, observer counting, status, cancellation, and SQLDelight integration qualified | third-party adapter added more glue; an owned value ledger duplicated the database | diagnostics, correctness, target support, or policy pressure favors a replacement |
| Live resource | keyed Capability coordinator + shared state | one resource/connection across observers | Cell/ViewModel socket ownership duplicates resources | another resource runtime fully replaces owned code |
| durable state | SQLDelight | four-target relational path qualified | transport cache alone does not meet durable needs | target/performance/tooling pressure |
| DB topology | one physical DB initially | simplest starting operations | DB-per-capability premature fragmentation | migration/invalidation/isolation pressure |
| refresh | common triggers/QoS + capability freshness | prevents herd without global domain brain | global coordinator duplicates resource framework | scheduler needs repeated product rules |
| batching | BatchHydrator feeds resource owners | optimization invisible to presentation | HomeRepository/observable hydrator becomes new state owner | backend data shape changes |
| DI | Koin + graph/compiler validation | familiar KMP integration | Metro/kotlin-inject switch not worth churn yet | validation unreliable or alternative materially simpler |
| observability | semantic APIs + Kermit/adapters | target/vendor independence | direct OTel/vendor API couples product code | one neutral target-complete option clearly simpler |
| composition | registry by surface need | preserves simple static screens | registry everywhere adds indirection | dynamic surfaces become dominant |
| SDUI | deferred dial | current product does not require global server ownership | full SDUI adds client/server/debug/test complexity | backend-controlled composition dominates |
| GraphQL | not now; future Capability Impl option | backend REST reality | speculative infrastructure; durable Wasm cache gap in Apollo SQLite | backend actually adopts GraphQL |
| module metadata | derive from graph/source | avoids drift | per-module YAML/custom DSL duplicates facts | derivation truly cannot express required fact |
| AI docs | few Skills + generated context | low ceremony, bounded tasks | per-Cell CLAUDE files become stale/reading cost | local deltas prove repeatedly necessary |
| AI vendor model | portable graph/CLI/verification + adapter Skills | same architecture/safety for humans and different agents | vendor-specific prompts as architecture source create lock-in/drift | an industry standard replaces adapters without losing deterministic seams |
| testing | layered rails + fixtures | each test proves a narrow property | goldens/E2E alone are incomplete | tooling noise invalidates a specific rail |

---

# Appendix E. Historical ideas explicitly not part of current Helix

Do not reintroduce these merely because they appear in older artifacts:

```text
Universal Cell<State, Event> superclass/interface
Universal Loading/Content/Error/Hidden CellState
Presenter-Composable as mandatory Cell logic holder
Direct QueryStore access from Cells
Global RefreshCoordinator owning TTL/product freshness
Registry for every Screen
Per-Cell API/Impl Gradle modules
Mandatory seven-file Cell structure
Mandatory per-Cell CLAUDE.md
Decompose as default alongside Navigation 3
Compile-time DI framework migration as baseline requirement
Goldens + E2E as sufficient safety model
GraphQL/Apollo as current architecture requirement
One physical SQLDelight DB as permanent invariant
Agent context-window size as the primary reason for modularity
```

---

# Appendix F. Source provenance

Moved to [docs/history/helix-design-history.md](../history/helix-design-history.md). The master remains the normative source; this section holds no rules.
