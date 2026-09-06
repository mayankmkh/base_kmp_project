# Helix KMP ADR catalog

This is the catalog of architecture decisions taken for Helix KMP, one `ADR-NN` heading each, so
an `ADR-NN` reference anywhere in the repository resolves here.
[`helix-kmp-source-of-truth.md`](helix-kmp-source-of-truth.md) remains the normative source: section 24 states the
governance rules these entries follow, and each entry's rule text lives in the section it names.
Repository-local ADRs are separate and live in [`adr/`](adr/), starting with
[`adr/0001-helix-adoption.md`](adr/0001-helix-adoption.md).

---


## ADR-01 - Specialized ownership rather than one application runtime

**Decision:** navigation, presentation instances, resources, durability, and shared expensive resources have specialized owners.

**Why:** they have different identity/lifetime semantics; a universal store/runtime increases coupling.

**Alternatives:** global MVI/app store, universal component runtime as the only owner.

**Revisit when:** product topology becomes dominated by nested autonomous component trees and the keyed helper becomes a general component runtime.

## ADR-02 - ViewModel UDF for stateful presentation

**Decision:** State + Action default; UiCommand/Output opt-in; ViewModel owns stateful Screen/Cell presentation.

**Why:** familiar, testable, already qualified with keyed ownership, avoids a second runtime.

**Alternatives:** Circuit presenter, Molecule DIY, Decompose component, global reducer.

**Revisit when:** presentation ceremony becomes a measured dominant tax and a mature alternative materially removes it without weakening ownership.

## ADR-03 - Feature modules with logical Cells, not module-per-Cell

**Decision:** Cells live inside owning Feature by default.

**Why:** module-per-Cell overpays build/IDE/reading cost.

**Revisit when:** consumer/team/context/ABI/build/test fanout evidence says a physical wall reduces blast radius.

## ADR-04 - Capability API hides resource/transport implementation

**Decision:** grouped Queries + intent Commands + stable models are presentation-facing contract.

**Why:** product vocabulary remains stable across Snapshot runtimes, SQLDelight, REST, WebSocket, and future replacements.

**Revisit when:** a transport-specific model becomes the true stable product contract and hiding it creates only duplication.

## ADR-05 - Snapshot orchestration primitive

**Decision:** superseded by ADR-43, which keeps the value in the database and reduces the runtime
to a domain-blind `SyncCoordinator`.

**Status:** the Store5 decision was superseded 2026-09-04 by an owned `SnapshotResource` runtime;
that runtime was superseded 2026-09-05 by ADR-43.

**Why superseded:** the representative Posts implementation required more adaptation and lifecycle
glue while the disabled library cache added no required behavior beyond refresh joining and a
source-of-truth barrier. The owned value-tracking replacement then reproduced the same second-owner
problem in project code; see ADR-43.

**Revisit when:** see ADR-43.

## ADR-06 - SQLDelight for durable relational state

**Decision:** current durable relational store; one DB default not invariant.

**Revisit when:** target support/performance/tooling or migration/invalidation/ownership pressure justifies split/replacement.

## ADR-07 - Navigation 3 + keyed embedded presentation owner

**Decision:** Navigation 3 for Screens; narrow `StatefulLazyItem`-style keyed owner for embedded smart Cells.

**Revisit when:** helper grows into component runtime, recurring lifecycle bugs, first-party replacement, or mature alternative deletes meaningful custom infrastructure.

## ADR-08 - Registry optional by surface class

**Decision:** registry for independently configurable composition; static Compose for static surfaces.

**Revisit when:** repeated retrofit pressure shows dynamic composition is the norm.

## ADR-09 - UiCommand allowed but fenced

**Decision:** transient local UI instruction only; correctness independent of delivery.

**Revisit when:** repeated audits show durable/domain state is still smuggled through despite enforcement.

## ADR-10 - REST/Snapshot runtime/SQLDelight are replaceable choices

**Decision:** REST-oriented backend now; no speculative GraphQL infrastructure.

**Revisit when:** backend/product economics adopt another transport/resource model.

## ADR-11 - Graph-first control plane

**Decision:** derive architecture facts from repository/build/source/ownership systems; no per-module YAML/custom dependency DSL.

**Revisit when:** a material fact cannot be expressed/derived and a small central override is insufficient.

## ADR-12 - Stable `helix-kmp` CLI with replaceable scripts

**Decision:** humans/agents/CI share one public command vocabulary.

**Revisit when:** CLI becomes a bottleneck or standard tooling provides the same workflows with lower owned cost.

## ADR-13 - Workflow AI Skills + graph-derived context

**Decision:** a few workflow Skills and focused context packets; concise root instructions; nested deltas only when necessary.

**Revisit when:** whole-repo comprehension becomes reliably superior at equal cost/accuracy or context tooling costs more than it saves.

## ADR-14 - Refresh orchestration has domain-blind QoS

**Decision:** external triggers + generic scheduling/backpressure common; freshness/product policy capability-owned.

**Revisit when:** scheduler repeatedly requires product-specific business rules.

## ADR-15 - Goldens are visual rail, not safety model

**Decision:** visual evidence is additive to independent correctness rails.

**Revisit when:** rendering churn makes selected golden mechanism too noisy.

## ADR-16 - Compose Multiplatform Web qualified to product needs

**Decision:** use current shared Web/Wasm target with permanent navigation/storage regressions.

**Revisit when:** Web requires SEO/accessibility/DOM/product capabilities that the shared Compose approach cannot reasonably provide.

## ADR-17 - Registry/SDUI is a dial, not global default

**Decision:** use dynamic composition where economics require it; do not server-drive everything.

**Revisit when:** backend-controlled composition/personalization/experimentation dominates product delivery.

## ADR-18 - Cell is a logical primitive, not a base class/module

**Decision:** Cell is a vocabulary/ownership concept implemented with ordinary Kotlin/Compose/ViewModel structures.

**Why:** preserves reuse/identity benefits without creating a private framework hierarchy.

**Revisit when:** a standard first-party abstraction emerges that exactly represents the concept and reduces custom terminology/boilerplate.

## ADR-19 - Cell file structure is complexity-driven

**Decision:** no fixed seven-file ceremony.

**Why:** generator removes writing cost but not reading cost.

**Revisit when:** empirical feature patterns justify a smaller universal skeleton or a different grouping.

## ADR-20 - Grouped Query interfaces, intent Commands

**Decision:** grouped read APIs, separate intent-shaped mutations.

**Why:** avoids trivial use-case class explosion while preserving product semantics for writes.

**Revisit when:** grouped interfaces become overly broad/unstable or per-operation contracts measurably improve reuse/testing.

## ADR-21 - Physical database topology is evidence-driven

**Decision:** one DB is a starting convenience, not a permanent ownership boundary.

**Revisit when:** migration/invalidation/performance/team isolation evidence fires.

## ADR-22 - Early real-Cell ergonomics are measured

**Decision:** Baseline v1 remains design-frozen, but first production Cells collect ceremony/context/debug/build evidence.

**Why:** POCs prove mechanics, not five-year ergonomics.

**Revisit when:** measured evidence shows a baseline default creates recurring reading/implementation tax.

---

## ADR-23 - Koin with constructor injection and graph/compiler validation

**Decision:** keep Koin as the DI mechanism while qualified; apply Koin compiler plugin 1.1.0 to
App, Feature, and Capability Impl roles; use its typed DSL for pure constructor definitions; keep
the entry point's module list a literal list of module names so the plugin validates the whole
graph; keep composition at App/implementation roots and service-locator access out of business/data
code.

**Why:** the literal module list buys whole-graph compile-time validation of every typed definition
on Kotlin 2.4.10 and all required KMP targets, at the cost of one rule test that holds the list
literal. What the plugin still cannot see is the inside of a definition lambda, so JVM runtime
verification and root resolution remain as complementary safety rather than migration leftovers.
Classes stay free of DI annotations and lifetimes stay declared on the definition through `onClose`.
See §18.7 for the qualification evidence and the verbatim diagnostics.

**Alternatives explored:** Koin Annotations and Metro 1.4.2 were both built as working spikes on
this toolchain on 2026-09-06 and measured, not compared on paper; both give whole-graph validation
including custom providers, and both were declined because they move the injector into business
classes or graph interfaces, replace `onClose` with a hand-written close registry, and add
generated indirection for coverage the runtime root-resolution test already provides. Also
considered: kotlin-inject and Dagger/Anvil-style compile-time DI. `kotlin-inject-anvil` is in
maintenance mode and is not a live contingency.

**Revisit when:** Koin's compiler plugin stops compiling on the pinned Kotlin version, blocks a
Kotlin upgrade for more than one minor release, or an alternative materially removes the remaining
runtime-only construction and verification complexity across required KMP targets without moving
the injector into business classes. Metro 1.4.2 is the written contingency.

## ADR-24 - Semantic observability seams, not vendor APIs

**Decision:** `FeatureObserver` and `ProductAnalytics` are stable application-facing seams; Kermit is the default neutral logging path and OpenTelemetry/vendor adapters are optional/qualified.

**Why:** target support differs, especially Web/Wasm, and operational telemetry should not become product-code vendor coupling.

**Revisit when:** one vendor-neutral stack has equal required-target support and clearly deletes adapter/maintenance cost.

## ADR-25 - Ktor is a generic HTTP mechanism; product endpoints stay in Capability Impl

**Decision:** generic HTTP configuration/client machinery may live in Foundation, while product endpoints, DTOs, mapping, auth/business semantics, and resource behavior live in owning Capability implementation.

**Why:** transport mechanism is reusable; product API shape is not generic infrastructure.

**Revisit when:** a different transport stack materially replaces Ktor or backend architecture changes; Capability API remains stable.

## ADR-26 - Durable outbox only for intent that must survive

**Decision:** use direct Commands for ordinary writes; use optimistic state when product semantics justify it; introduce a durable outbox only when user intent must survive offline/process death/ambiguous delivery.

**Why:** an outbox gives idempotent durable intent but is too much machinery for every mutation.

**Revisit when:** offline-first command semantics become universal enough that common command infrastructure deletes rather than adds complexity.

## ADR-27 - Scope is logical; architecture metadata is derived

**Decision:** `scope` is a logical graph/context grouping, not a new Gradle parent/aggregator; role/dependency/platform/owner/public-surface facts come from operational sources rather than per-module YAML.

**Why:** duplicated metadata drifts and makes graph/context/CI disagree.

**Alternatives:** `:scope:*`/`:capsule:*` aggregators, per-module architecture descriptors, custom dependency DSL.

**Revisit when:** a required architecture fact genuinely cannot be derived or centrally represented without harmful ambiguity.

## ADR-28 - AI workflows are vendor-portable adapters over the same control plane

**Decision:** use a few workflow Skills and concise agent instructions, but keep repository graph, `helix-kmp`, JSON outputs, deterministic verification, and ownership rules as the portable contract.

**Why:** architecture must work for humans and multiple agent systems; Skills should not become a second source of truth.

**Revisit when:** a standardized agent protocol makes separate Skill adapters redundant, while retaining the same repository/control-plane semantics.

## ADR-29 - Markdown master source, derived HTML and audience documents

**Decision:** this exhaustive Markdown master is the reasoning/documentation source of truth; human-facing technical/management/QA/contribution pages are generated/derived, with HTML as the main reading surface and PDF/DOCX as optional snapshots.

**Why:** Markdown is diffable, searchable, agent-friendly, versioned with code, and can drive multiple formats without independent copies drifting.

**Revisit when:** another source format provides equal diffability/automation/agent ergonomics and materially improves maintenance.

## ADR-30 - Starter conversion is a one-time adoption workflow, not a permanent migration subsystem

**Decision:** for the lightly configured starter repository, inspect/keep/reshape/replace directly, install the Helix foundation and reference slice, then retire the adoption playbook.

**Why:** large-system LEGACY/MIGRATING taxonomies and strangler infrastructure would be disproportionate to the starter's size.

**Revisit when:** the repository becomes a genuinely large legacy migration before adoption completes.

## ADR-31 - Synchronized reads expose one validatable ResourceObservation contract

**Decision:** remotely synchronized Capability reads use the single `ResourceObservation<T : Any>`
contract from `:foundation:resource` (`foundation_api`) when value plus refresh/failure semantics
matter; simple local reads may remain `Flow<T>`. A null value with `Idle` is the deliberate
confirmed-absence state; `initial()` and `SyncStatus.toOperation` keep never-synchronized keys out
of that state.

**Why:** a plain domain-value flow cannot consistently express loading, refresh with a retained value, cached-offline, or failed refresh, while per-Capability copies would immediately drift.

**Revisit when:** a simpler standard resource primitive provides the same transport-neutral semantics and legal-state guarantees.

## ADR-32 - App owns application runtime coroutine parent

**Decision:** App creates/cancels the parent in-process runtime scope; Capability resource/coordinator owners create child jobs. Shared resources never borrow ViewModel scope and never use `GlobalScope`.

**Why:** Resource lifetime and presentation lifetime are intentionally independent.

**Revisit when:** a target-complete standard runtime provides equivalent structured application/resource scope ownership.

## ADR-33 - FeatureInstanceKey uses typed host placement

**Decision:** presentation identity is constructed once from surface + Cell type + a `CellPlacementId` owned by the host. `ResourceKey`/domain IDs cannot be passed directly to the factory, and debug hosts fail on duplicate active instance keys.

**Why:** ResourceKey/index/string-convention identities collide or leak state under duplicate placements and reorder.

**Revisit when:** a standard presentation runtime supplies equivalent structured instance identity and duplicate protection.

## ADR-34 - Identity API owns correctness-bearing session state; Foundation network owns auth mechanics

**Decision:** Identity owns session/token refresh/logout semantics. `:capability:identity-api` exposes observable `SessionState` and the optional typed `IdentityEvent.SessionEnded`. Foundation network owns generic bearer injection/401 retry behind an Identity-supplied credential contract. Navigation/cleanup correctness follows session truth or explicit orchestration, never transient event delivery alone.

**Why:** authenticated transport is cross-cutting but session truth still needs one product owner and must not depend on an event collector being active.

**Revisit when:** backend/platform authentication changes materially.

## ADR-35 - Verification has fast and full tiers

**Decision:** humans/agents iterate with `helix-kmp verify --fast --affected`; full Android/iOS/Desktop/Web matrix is CI/release/qualification.

**Why:** verification latency is delivery architecture.

**Revisit when:** full-matrix technology becomes fast enough to collapse tiers without slowing iteration.

## ADR-36 - Standard primitive first; bespoke primitive requires complete reference

**Decision:** prefer standard KMP/Compose/Kotlin mechanisms. Private Helix primitives require unmet need, complete executable reference, regression, diagnostics, and deletion condition.

**Why:** private primitives impose learning/context/maintenance cost.

**Revisit when:** ecosystem primitives replace the owned mechanism or measured custom value remains clearly higher.

## ADR-37 - Dynamic registry assembly belongs at App composition

**Decision:** static hierarchical Feature composition may use a child public entry; registry-driven hosts depend on generic registry contracts while App assembles concrete registrations.

**Why:** dynamic Home/Discover must not become the compile-time root of every registered Feature.

**Revisit when:** composition becomes server-driven enough to require another typed composition protocol.

## ADR-38 - Dependency role policy is default-deny and machine-decidable

**Decision:** role dependency legality and stable rule IDs live in Section 9's machine-readable policy. Unlisted pairs are denied. Physical `:foundation:*` modules are classified as `foundation_api` or `foundation_runtime`, and Feature -> Feature composition is conditionally allowed only through a machine-checkable child `.api` public presentation surface.

**Why:** allow+deny gaps, prose predicates, or broad undifferentiated Foundation access create ambiguous validator behavior and violate one-fact-one-source.

**Revisit when:** tooling requires moving the canonical policy to another authoritative machine-readable source while preserving the same decidability/no-drift guarantees.

## ADR-39 - Product configuration/design/localization/background work have explicit owners

**Decision:** typed flags/experiments are a product Capability; design tokens/theme are UI/design-system; localization mechanism is UI/localization with cohesive Feature-local strings; durable outbox is Capability-owned while background execution is best-effort Platform machinery.

**Why:** otherwise these cross-cutting concerns become unowned globals.

**Revisit when:** product/platform structure materially changes these boundaries.

## ADR-40 - Cross-module CellSpec is open, not sealed

**Decision:** `CellSpec` is a plain interface in `:foundation:presentation`; concrete specs are Feature-owned.

**Why:** Kotlin sealed direct subtypes must remain in the declaring module/package, which conflicts with independently compiled Features implementing a shared registry contract.

**Revisit when:** registry ownership moves into one compilation module or a first-party typed registry eliminates the contract.

## ADR-41 - Dependency pins are repository facts, not transcribed upstream-latest prose

**Decision:** exact current pins come from repository version catalogs/lockfiles; qualification comes from project evidence; this master records decisions, historical POC provenance, status, and upstream source URLs without copying fast-decaying "latest stable/beta/alpha" numbers.

**Why:** upstream release numbers can become stale within days and should not create a second version authority.

**Revisit when:** dependency/qualification tooling can inject immutable released snapshots into derived docs automatically without manual drift.

## ADR-42 - Control-plane staging has one canonical machine-readable block

**Decision:** P0/P1/P2 availability is defined only in Section 22.1.1. P0 precedes the first real Cell, P1 is required before adoption completion, and P2 is evidence-driven. Root agent instructions are phase-aware; Appendix A, now in [`templates/agents-and-skills.md`](templates/agents-and-skills.md), is the P1-complete/post-adoption template.

**Why:** duplicate staging lists can tell agents to run commands that are not implemented.

**Revisit when:** the control plane reaches a mature steady state where phases no longer provide useful adoption semantics.

## ADR-43 - Sync coordination is domain-blind and the database owns the value

**Decision:** `:foundation:resource-runtime` ships `SyncCoordinator<Key>` plus the small pieces
that connect it to a Capability (`observations` and the per-Capability `CommandBridge`); it owns no
value.
It starts or joins one worker per key, skips keys attempted within `minInterval`, counts observers
around a Capability-supplied upstream flow, retries observed keys whose last attempt failed offline
whenever its `retryTriggers` flow emits, and exposes a per-key `SyncStatus` ledger bounded by
`maxEntries`. It never reads, caches, compares, or re-reads values.
Capability implementations observe SQLDelight directly, persist their own "synchronized at least
once" marker in the same transaction as the rows, apply that marker to the value flow, and hand the
flow to `observations`, which applies the contract's `SyncStatus.toOperation` mapping.
`ResourceFreshness` is deleted from the contract and `SyncStatus` is added. Supersedes ADR-05.

**Why:** the owned `SnapshotResource` runtime that replaced Store5 on 2026-09-04 tracked a per-key
confirmed value and derived `FRESH`/`STALE` by comparing every durable emission with a completion
re-read. Its release-gate review found that this made the runtime the second owner of the value: it
needed invalidation generations, self-heal syncs, structural equality on every emission, and re-read
gates to stay consistent with the database, and a detail write to a shared table could still change
the feed's freshness without any feed sync. Freshness derived from in-memory comparison is not a
durable fact, and no consumer used it beyond a boolean the Feature has since dropped. Removing the
value from the runtime removes that class of consistency bug and shrinks the runtime to a
mutex-guarded map with a state table.

**Alternatives considered:**

- Keep the owned value ledger and land the remaining repairs. Rejected: each repair added state
  that duplicated what SQLDelight already knows, and the correctness argument rested on
  interleavings that only a contention test could exercise.
- Revert to the Store5 adapter as of commit 0240602. Rejected: the reasons in ADR-05 still hold,
  and its disabled cache still made the library the second value owner.

**Revisit when:** a Capability needs a domain-blind age or staleness policy that cannot live in its
own rows, or a qualified third-party primitive provides per-key joining, due-checking, observer
counting, and status without owning the value.

## ADR-44 - Two-lane command results: Outcome with refusals in T

**Decision:** every Capability command returns `Outcome<T>`. `Failed(Problem)` means no decision
was reached. A completed refusal is a command-specific sealed case inside `T`, with validation
details represented by `Violation<F>`. Capability implementations map endpoint answer statuses
before the single network-failure bridge classifies and logs everything unmapped.

**Why:** one envelope at every command boundary keeps infrastructure failures separate from
product decisions, preserves exhaustive domain results, prevents transport and kotlin-result types
from leaking into Capability APIs, and gives every failed classification one logging path.

**Revisit when:** Kotlin can express a more precise command envelope without dead branches at
`Unit` commands, or product evidence shows that refusals need a shared cross-capability contract.
