# Helix KMP design history and alternatives explored

This is the historical record of how the Helix KMP architecture was arrived at: the proposal
stages, the runtime and data alternatives explored, the external critiques and their accepted
corrections, and the provenance of the master source. It states no rules.
[`helix-kmp-source-of-truth.md`](../architecture/helix-kmp-source-of-truth.md) remains the
normative source; sections 5.1 to 5.13 below keep their original numbers so `§5.n` references
still resolve.

---


This section is historical. Its purpose is to preserve reasoning so future teams do not restart old debates without new evidence.

## 5.1 Stage A - Conventional KMP screen architecture

The starting mental model was broadly:

```text
Screen
  -> ViewModel
      -> Use Case
          -> Repository
              -> API / DB
```

Strengths:

- familiar;
- easy for simple screens;
- AndroidX ViewModel lifecycle semantics are well understood;
- use cases can isolate business intent.

Observed pressure:

- complex feed widgets remained screen-owned;
- reuse of business/data behavior and reuse of presentation were conflated;
- screen ViewModels grew as widgets accumulated;
- independently hosted widget identity/lifetime remained unsolved;
- shared sockets/batching/resource state tended to leak upward.

The lesson retained by Helix: keep ViewModel/UDF where it is useful, but scope ownership to the logical presentation unit rather than assuming Screen is always the unit.

## 5.2 Stage B - AI-First Modular KMP Blueprint

The first comprehensive architecture introduced the durable module roles still present in Helix:

```text
:app:*
:feature:*
:capability:*-api
:capability:*-impl
:ui:*
:foundation:*
:platform:*
:platform:*-api
:platform:*-impl
:storage:*
:testkit:*
:tooling:*
:build-logic
```

Key ideas retained:

- locality before abstraction;
- reuse the smallest useful unit;
- product meaning above technical mechanism;
- explicit dependency direction;
- presentation/resource/durable/workflow state have different owners;
- scope is logical, not a new Gradle parent/aggregator;
- graph-first architecture enforcement;
- KMP four-target qualification;
- generated golden paths and AI Skills;
- `doctor`, context, affected verification, refactoring recipes;
- POC-first runtime/library qualification;
- observable feature identity;
- stable vendor-neutral observability seams.

Early blueprint conventions later changed:

- individual read use-case classes such as `ObserveLiveScore` -> grouped capability Query interfaces;
- `Effect` terminology -> optional fenced `UiCommand`;
- generic smart widget notion -> explicit Cell primitive;
- feature-to-feature composition rules -> clearer hierarchical public-entry/no-peer-coupling rule;
- refresh use cases -> split trigger orchestration + capability freshness semantics.

## 5.3 Runtime exploration - Navigation 3 vs Decompose

### Navigation 3

Selected because the required destination behavior was validated while preserving a smaller runtime vocabulary:

- user-owned back stack;
- typed route keys;
- entry-scoped ViewModel ownership/destruction;
- adaptive scene support;
- Android/iOS/Desktop/Web Compose Multiplatform support;
- navigation implementation isolated at app/route boundary.

Gap: embedded smart list items are not navigation destinations, so Helix owns one narrow keyed ViewModel-owner helper.

### Decompose

Seriously evaluated because it provides:

- component-local lifecycle;
- `StateKeeper`/`InstanceKeeper`-style retention;
- back handling;
- nested child navigation;
- child-item/panel ownership patterns;
- Compose-independent logic layer.

Why not selected by default:

- current product requirements did not need a general component tree runtime;
- one small qualified keyed owner solved the embedded-widget case;
- using both Nav3 and Decompose as general ownership systems would add vocabulary and integration cost.

Escape hatch:

Reconsider Decompose if the product becomes dominated by nested autonomous mini-app/component trees with their own navigation, back, saved state, retained child hierarchy, and independent lifecycle.

## 5.4 Stage C - Fable/Claude "Cell Architecture" proposal

A later adversarial proposal reframed the app around four planes. In this discussion **Cell meant a mobile/client presentation component**, not the unrelated backend/AWS "cell-based architecture" pattern.

The proposal's original conceptual stack was:

```text
Screen plane
  thin composition / ordered CellSpecs / Host services
        |
        v
Cell plane
  autonomous presentation units
        |
        v
Data plane
  QueryStore / shared SSOT / refresh / batching
        |
        v
Rails plane
  generators / compiler+CI rules / agent context / goldens
```

Its original Cell ceremony was roughly:

```text
PortfolioCardCell.kt
PortfolioCardPresenter.kt
PortfolioCardUi.kt
PortfolioCardFixtures.kt
PortfolioCardPresenterTest.kt
PortfolioCardUiTest.kt
CLAUDE.md
```

and the presenter idea was conceptually closer to:

```kotlin
@Composable
fun present(...): CellState
```

with Cells reading a shared QueryStore directly. Screens were intended to become thin ordered `CellSpec` hosts; even a one-Cell screen could be treated as a degenerate registry composition. Data refresh/freshness was centralized under a broad RefreshCoordinator, and the proposal leaned toward stronger physical Cell isolation.

These details are preserved here because several final decisions are explicit reactions to them: **Cell survived; mandatory presenter/CellState/QueryStore/registry/seven-file/per-Cell-doc/module ceremony did not.**

Its strongest ideas were:

- screen is too coarse for independently useful stateful widgets;
- Cell as a bindable/autonomous presentation unit;
- no sideways peer coupling;
- explicit per-instance identity;
- shared resource identity distinct from presentation instance;
- fixtures as review surface;
- generator/rails as architecture;
- a flight recorder/resource inspector;
- quantitative split/extraction rules;
- architecture-kit versioning and codemod migrations.

The proposal also suggested several ideas that were intentionally not adopted as-is:

| Proposal | Final Helix decision |
|---|---|
| Cell presenter is a Composable function rather than ViewModel | Keep ViewModel/UDF as default qualified presentation owner; Circuit/Molecule presenter model remains an alternative if measured ceremony warrants it. |
| universal generic `Cell<State, Event>` contract | Reject. Cell is a logical primitive, not a required class/interface. |
| universal `Loading | Content | Error | Hidden` CellState | Reject. State is feature-specific; rendering nothing is legal but `Hidden` is not a mandatory state. |
| direct `QueryStore` access from Cell | Reject. Presentation talks to product-facing Capability Queries. |
| one global RefreshCoordinator owns TTL/freshness | Modify. Common layer owns external triggers + generic QoS; Capability owns resource freshness/retry/invalidation semantics. |
| registry for every screen | Modify. Registry only where product can independently change presence/order/variant. |
| one API/Impl module pair per Cell | Reject. Cells live inside Feature modules by default; physical extraction is evidence-driven. |
| Decompose behind facade as default runtime | Reject for current topology; retain as escape hatch. |
| compile-time DI framework such as Metro/kotlin-inject | Do not switch by default; retain Koin + graph/compiler validation while qualified. |
| flight recorder/resource inspector | Adopt and strengthen. |
| fixture gallery | Adopt. |
| no-sideways | Adopt with clarification: hierarchical composition is allowed; peer implementation coupling is prohibited. |
| generator + hard rails + soft context | Adopt, but avoid mandatory per-Cell documentation ceremony. |

### 5.4.1 Historical proposal mechanics and tool suggestions - preserved for provenance, not normative

The Cell Architecture proposal contained several concrete implementation suggestions and threshold rules. They are recorded here so future reviewers can distinguish **what was actually proposed** from the final Helix policy. None of the items in this subsection are current requirements unless restated elsewhere in the normative sections.

#### Original generation and testing loop

The proposal imagined a generator similar to:

```text
./tools/new-cell.sh portfolio PortfolioCard
  -> Cell contract / key / params
  -> presenter
  -> UI
  -> fixtures
  -> presenter tests
  -> screenshot tests
  -> per-Cell CLAUDE.md delta
  -> registry wiring
  -> RefreshSpec wiring
```

Specific ecosystem examples proposed for that loop included:

- **Molecule + Turbine-style testing** for presenter/state production;
- **Roborazzi or Paparazzi** for Android/Compose screenshot goldens;
- **swift-snapshot-testing** as an iOS analogy;
- a fixture-gallery screen as the non-developer review surface;
- Circuit's code-generation approach as evidence that generators can make component ceremony cheaper to produce;
- **Tuist scaffold** as an iOS-side analogy for golden-path generation.

Final Helix treatment:

- fixtures/gallery and deterministic visual review were adopted;
- exact screenshot/test libraries are **not architecture requirements**;
- presenter-specific Molecule/Turbine testing is not the default because ViewModel/UDF remained the baseline;
- generation is retained, but the generator must scale file count with complexity rather than stamping a fixed seven-file Cell;
- per-Cell `CLAUDE.md` is not mandatory; root `AGENTS.md` plus generated context packets and nested delta instructions are preferred.

#### Original runtime alternatives for embedded components

The proposal discussed several ways to retain independently stateful items:

```text
Circuit SubCircuit / retained presenter composition
Decompose child-item/component ownership
first-party or custom Compose retain{...}-style retention
custom keyed owner
```

Final Helix treatment:

- Nav3 owns Screen navigation lifetime;
- a narrow keyed `StatefulLazyItem(FeatureInstanceKey)` owner handles smart embedded Cell lifetime;
- Decompose remains the escape hatch if the helper grows into a component runtime;
- Circuit remains a reconsideration candidate if presentation ceremony becomes a measured tax;
- if Compose/Navigation ships first-party keyed ownership with equivalent semantics, Helix should delete its custom helper rather than preserve owned infrastructure.

#### Original fixed split heuristics

The proposal suggested intentionally simple mechanical thresholds:

```text
Split screen-embedded logic into a Cell when:
  - it is used on a second surface, OR
  - it owns an independent refresh cadence, OR
  - it exceeds roughly 150 lines of screen-embedded logic.

Promote a Cell/feature boundary physically when:
  - the module exceeds roughly half the smallest supported agent context window, OR
  - a second owning team appears.
```

These were useful provocations because they replaced subjective "this feels big" judgment with observable evidence. Final Helix deliberately **softened the hard numbers**:

- second independent consumer/surface remains strong evidence;
- independent lifetime/refresh remains strong evidence that presentation ownership should be separated;
- line count alone is too gameable and language/style-dependent to be a law;
- context size is a doctor signal, not a permanent architecture threshold;
- team ownership, ABI/dependency fanout, build/test fanout, exception pressure, co-change, and repeated illegal-edge attempts are evaluated together;
- `helix-kmp doctor` recommends extraction/merge with evidence; boundary moves remain reviewed changes.

This change is important for long-term AI evolution: context-window fit is a useful current optimization, but Helix should still make sense if future agents can comprehend much larger repositories.

#### Historical module-visibility/tooling analogies

The proposal also referenced:

- Gradle module visibility and Kotlin `internal`;
- API/Impl module pairs;
- **SPM/Bazel target visibility** as analogies from other ecosystems;
- affected-module CI to reduce agent feedback latency;
- ABI dumps to make public-contract changes explicit;
- compile-time DI options such as **Metro**, **kotlin-inject**, and **Dagger/Anvil** for stronger missing-binding diagnostics.

Final Helix keeps the ideas - visible dependency boundaries, ABI awareness, affected verification, and DI graph verification - while avoiding a requirement to reproduce the exact external tools or switch DI frameworks without measured benefit.

## 5.5 Cell naming clarification

The final vocabulary intentionally keeps **Cell** as a technical primitive.

A proposed rename to "Presentation Unit" was rejected because Cell is shorter, distinctive, and already captures the independently hostable presentation concept.

However, Cell is not allowed to trigger a biology taxonomy explosion. Do not invent architecture nouns such as Genome/Gene/Organ/Protein merely for theme consistency.

## 5.6 Stage D - Merged Helix KMP architecture

The merged design kept the original blueprint's familiar KMP/module/runtime foundations, adopted the strongest Cell/control-plane/resource-debug ideas, and removed unnecessary ceremony.

Core synthesis:

- Feature remains the physical/product presentation scope;
- Screen remains a navigation destination;
- Cell becomes the autonomous stateful embedded presentation primitive;
- ViewModel/UDF remains default presentation owner;
- capability APIs become grouped Queries + intent Commands;
- resource taxonomy becomes Snapshot / Live / Projection;
- `ResourceKey`, `FeatureInstanceKey`, `RouteKey` are explicitly distinct;
- common refresh owns triggers/QoS only;
- capability owns freshness/resource semantics;
- physical DB topology and module granularity are defaults, not dogma;
- control plane is graph-first and AI-operable;
- fixtures and runtime inspector are first-class architecture seams.

## 5.7 Final external critique and accepted corrections

A later review raised seven substantive criticisms. The architecture incorporated the useful corrections.

### Criticism 1 - AI context benefit is a decaying asset

Concern: context-window optimization may become less valuable as agent models improve, while architectural ceremony remains.

Accepted correction:

- AI context fit is a **derived quality**, not the primary justification for Cells/modules;
- durable reasons remain ownership, low blast radius, reuse, testability, observability, and safe parallel work;
- Helix may simplify context tooling if whole-repo comprehension becomes reliably superior.

### Criticism 2 - Generator amortizes writing, not reading

Concern: generated seven-file Cells still cost humans/agents reading time.

Accepted correction:

> **File count is not an invariant.**

A simple Cell may be two files. A complex Cell may be several. Structure scales with complexity.

### Criticism 3 - Architecture frozen before enough real Cells

Concern: POCs validate mechanics but not long-term feature ergonomics.

Accepted correction:

- Baseline v1 is design-frozen to stop theoretical churn;
- core seams are POC-qualified;
- the **first roughly 10 real production Cells after adoption** should be treated as an ergonomics pilot (not toy/demo Cells);
- the number 10 is a practical sampling window, not a permanent Helix law;
- measured pain can fire ADR revisit conditions before conventions spread across dozens of features.

Suggested early metrics:

- files touched per feature change;
- architecture-ceremony lines vs useful product logic;
- context loaded per agent task;
- agent retries/repair loops;
- time-to-fix;
- build/test fanout;
- architecture exceptions;
- resource-debug time;
- Cell reuse count.

### Criticism 4 - One SQLDelight database can become a new hotspot

Accepted correction:

- one physical DB is a convenient starting topology, **not an invariant**;
- logical table/resource ownership remains capability-specific;
- monitor migration/invalidation/co-change/performance pressure;
- split physical DBs only when evidence says isolation pays.

Known cost retained: SQLDelight reactive invalidation can be coarser than entity-normalized caches, and mapping server shapes into relational tables is real work.

### Criticism 5 - Refresh orchestration needs arbitration

Accepted correction:

Common refresh infrastructure owns domain-blind QoS/backpressure:

```text
Priority: CRITICAL_VISIBLE | VISIBLE | BACKGROUND | PREFETCH
Network:  ANY_NETWORK | UNMETERED_PREFERRED | UNMETERED_ONLY
```

It may own:

- global concurrency limits;
- backpressure;
- cancellation;
- deduplication;
- metered/data-saver policy;
- battery/background policy.

It must not hard-code product semantics such as "portfolio is more important than promo."

### Criticism 6 - Goldens are too fragile to be a safety model

Accepted correction:

Goldens are one **visual rail**, never the safety model. Behavioral, architecture, resource, integration, accessibility, and E2E checks remain independent gates.

Framework-upgrade golden churn should be reviewed separately from feature changes where practical.

### Criticism 7 - Helix is bespoke

Accepted mitigation:

- keep the vocabulary small;
- use standard KMP/Gradle/Compose/ViewModel/Flow mechanisms underneath;
- hide bespoke tooling behind a stable CLI;
- keep generated conventions simple;
- maintain `Revisit when` escape hatches;
- avoid multiplying private nouns and frameworks.

## 5.8 GraphQL/Relay/Apollo alternative explored and deferred

A later critique argued that Helix's REST-oriented data plane resembles capabilities normally provided by Relay/Apollo-style normalized GraphQL clients. The correspondence proposed in that review was approximately:

| Helix/REST concern | Relay/Apollo analogue |
|---|---|
| BatchHydrator / aggregate BFF fetch | composed GraphQL surface query / fragment composition |
| `ResourceKey` / business identity | normalized entity/cache key |
| Projection/selectors | fragment-derived reads / cache watchers |
| table-granular SQL invalidation | entity-level normalized cache updates |
| REST bootstrap + WebSocket convergence | query/subscription writes into one normalized entity store |

Potential upside if the backend supported GraphQL:

- fewer hand-maintained aggregate hydrators;
- entity-level deduplication and consistency;
- more precise invalidation than relational table-level notifications;
- query/mutation/subscription convergence through one normalized cache;
- backend query shape composed from client data requirements.

The review initially recommended a four-target Apollo Kotlin spike before the first real Cell because, if it qualified, it might materially simplify the data implementation while leaving the Cell plane intact.

That recommendation was then **explicitly cancelled** after the product/backend constraint was clarified: the backend does not support GraphQL and is not expected to support it in the near future. Building GraphQL infrastructure or a speculative POC would optimize for a path the product does not currently have.

Apollo Kotlin was still evaluated conceptually because it demonstrates the replaceability of the Helix boundary:

- Apollo Kotlin runtime and its in-memory normalized cache support `wasmJs`;
- Apollo's SQLite normalized cache did not provide the required JS/Wasm durable-cache path in the reviewed snapshot;
- therefore Apollo would not automatically replace cross-target durable/offline storage even if GraphQL were introduced.

Final decision:

- **do not run or fund a GraphQL-specific POC now**;
- retain REST/owned Snapshot runtime/SQLDelight/WebSocket implementation;
- keep Capability APIs transport-neutral so GraphQL/Apollo can be introduced later if backend/product economics change;
- if GraphQL is introduced, keep Apollo/schema/generated transport models below Capability implementation rather than exposing raw GraphQL fragments/models as Cell contracts;
- do not redesign Helix around fragment-colocated presentation contracts merely to mimic Relay.

This is an important flexibility test: changing the resource/transport implementation should not require redesigning Cells, ViewModels, or Capability consumers.

## 5.9 SDUI / server-driven composition

Server-driven UI was explored as a spectrum, not a binary architecture choice.

Final decision:

- client-driven composition is default;
- registry + typed Cell descriptors provide much of the client-side seam needed for experiments/personalization;
- full SDUI is justified only when backend-controlled ordering/components/experiments/personalization become dominant product economics;
- Cells and Capabilities remain reusable if the composition source later becomes server-driven.

## 5.10 Lean modular-monolith retreat path

A deliberately simpler alternative was considered:

- keep module/dependency invariants;
- keep capability/resource SSOT;
- use one Screen ViewModel with hoisted widget state;
- skip keyed Cell runtime/registry unless necessary.

This can capture much of Helix's value for small/static products. It loses on the core case that motivated Helix: multiply instantiated autonomous widgets with independent presentation state and shared resource lifetime.

Helix should be allowed to simplify toward this shape if real product evidence shows autonomous Cells/runtime machinery do not pay for themselves.

---

## 5.11 Universal/global application store alternative

A Redux/MVI-style universal application store was considered as a simplifying answer to shared state and cross-screen consistency. It was not selected as the default architecture.

Why it is attractive:

- one obvious place to observe application state;
- deterministic reducer/event vocabulary;
- potentially strong replay/debug tooling;
- familiar mental model for teams coming from Redux-style systems.

Why Helix does not choose it as the universal owner:

- navigation state, local presentation state, shared server/resource state, durable offline intent, media/socket lifecycles, and platform state have **different identities and lifetimes**;
- one store tends to become an accidental common owner and raises coupling/blast radius;
- independent reusable Cells would still need instance identity and lifetime rules;
- resource mechanisms such as sockets, DB transactions, batching, and retry do not become simpler merely because their snapshots are mirrored into one reducer;
- it creates another global runtime/framework commitment when standard ViewModel/Flow + Capability resources already cover the required cases.

A reducer/state machine remains valid **inside a genuinely complex workflow** when it improves that owner. The rejected idea is making one global reducer/store the mandatory ownership model for the whole application.

## 5.12 Master Source 1.1 adversarial implementation-closure review

A subsequent Claude review of all 6,374 Master 1.1 lines concluded that the application architecture should not be reopened, but identified implementation seams where agents could still diverge.

The review explicitly preserved the four information classes, boundary test, three identities, Capability-owned subscriber-counted live resources, trigger/QoS versus Capability freshness split, GraphQL deferral, merge-as-valid-evolution, and human/agent verification/permission parity.

Master 1.2 disposition:

| Review point | 1.2 disposition |
|---|---|
| repeated normative facts inside master | accept problem; retain one exhaustive master but designate canonical anchors/policy blocks and generated repetitions |
| dependency matrix not machine-readable | accept; add canonical machine-readable dependency policy |
| Capability API/Impl pair too eager | modify; local one-consumer behavior stays local, extracted reusable Capability uses API/Impl by default |
| Platform API/Impl pair too eager | accept/modify; simple platform mechanism may be one `:platform:*` expect/actual module |
| enforcement rails not ranked | accept; add enforcement-strength ladder |
| control plane oversized before first Cells | accept staging, not removal |
| dynamic Home can become transitive Feature root | accept for registry surfaces; App populates registry |
| Query error/freshness contract absent | accept; add `ResourceObservation<T>` |
| shared-resource coroutine scope absent | accept; App parent scope + Capability child jobs |
| Cell ViewModel acquisition absent | accept; add complete reference |
| FeatureInstanceKey examples inconsistent | accept; define construction contract |
| auth/session ownership absent | accept; Identity owns session/refresh, Foundation network owns generic mechanism |
| FeatureObserver boilerplate | accept; non-generic trace delegate |
| third-party Snapshot orchestration cost could exceed value | revisit condition fired 2026-09-04; use the owned runtime |
| flags/localization/theme/background replay unplaced | accept; assign owners |
| verify latency missing | strong accept; fast/full tiers |
| private primitives need complete references | accept; Standard Primitive First |
| context tooling may decay | accept priority correction |
| OpenAPI generation | conditional accept when backend contract is authoritative |
| Compose `retain` may replace keyed host | immediate qualification task; do not assume equivalence before four-target proof |

No Helix law changed. Master 1.2 is an implementation-closure revision.

## 5.13 Master Source 1.2 verification review and correctness pass

Claude then verified the 1.2 file against its own changelog rather than accepting the changelog on faith.

Accepted defects/corrections:

1. canonical Koin ViewModel parameter syntax needed the `ParametersDefinition` lambda form;
2. cross-module `CellSpec` could not be `sealed`;
3. dependency policy needed default-deny semantics and executable conditional predicates;
4. Foundation policy/prose needed one machine-decidable classification;
5. `ResourceObservation` needed an explicit module home and concrete reference mapping;
6. Output collection needed stable callback handling and lifecycle-aware collection;
7. transcribed "latest upstream version" facts were already stale and must not be master truth;
8. control-plane staging was duplicated/inconsistent between Section 22 and [Section 29, now in `helix-adoption-model.md`](helix-adoption-model.md);
9. placement identity needed a stronger type;
10. `ResourceObservation` legal combinations needed to be explicit;
11. `SessionEnded` needed an API-module home and correctness-bearing session truth;
12. Mermaid labels needed HTML/newline-safe syntax for generated docs.

Master 1.3 fixes these without reopening the four laws or core architecture.


---

# Appendix F. Source provenance


## F.1 Conversation-coverage re-audit

A second explicit coverage audit was performed after Master Source edition 1.0. The audit compared this file against the architecture-thread decisions rather than only the latest derived docs. It reconfirmed coverage of:

- original Blueprint module/dependency/AI-control-plane decisions;
- Fable/Claude Cell Architecture proposals and rejected mechanics;
- final Cell/ViewModel/Capability/Resource/runtime synthesis;
- runtime/data/DI/observability POCs and qualification statuses;
- all seven later architecture criticisms and accepted corrections;
- GraphQL/Apollo proposal and later cancellation;
- naming alternatives/public-collision reasoning;
- CLI/scripts/Skills/context/agent-permission model;
- technical and non-technical documentation catalogs;
- the greenfield-playbook false start and final disposable starter Adoption Playbook;
- documentation-format/readability decisions.

The re-audit added missing **documentation-history and provenance detail**, not a change to the four Helix laws or the frozen architecture shape.

## F.2 Source set

This master was synthesized from the complete architecture discussion and its generated artifacts, including:

- early feature/navigation architecture drafts;
- AI-First Modular KMP Architecture Blueprint revisions through v1.2;
- production baseline documents;
- the external Cell Architecture review artifact;
- adversarial Fable/Claude architecture reviews;
- runtime/data/DI/observability POC outcomes;
- naming/public collision review;
- Helix KMP Baseline v1;
- Helix KMP Documentation Package Documentation Edition 2;
- the standalone Helix KMP Adoption Playbook v2;
- subsequent clarifications in the architecture discussion, including Cell not being a base class, GraphQL deferral, refresh QoS, DB topology, goldens, and AI/control-plane completeness;
- the Claude adversarial review of Master Source edition 1.1 and the implementation-closure disposition in Section 5.12;
- the Claude verification review of Master Source edition 1.2 and the correctness/no-drift fixes recorded in Section 5.13.

After approval, this master should supersede those artifacts as the **reasoning and documentation source of truth**. Repository code/build/ownership systems remain operational sources for derived facts as defined in Section 0.
