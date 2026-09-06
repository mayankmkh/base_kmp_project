# Helix KMP background: outcomes, influences and references

These are the background essays behind Helix KMP: the management outcomes it is measured by, the
external architectures that influenced its reasoning, and the public URLs used when qualifying
its technical choices. None of it is a dependency or a requirement.
[`helix-kmp-source-of-truth.md`](helix-kmp-source-of-truth.md) remains the normative source; subsections keep their
original `33.n` and `37.n` numbers.

---

# 33. Management outcomes and metrics


Management should track outcomes rather than module counts.

## 33.1 Delivery metrics

- time from product intent to verified change;
- review turnaround;
- percentage of routine work completed without platform specialist;
- AI-agent retry/repair loops;
- context loaded per bounded task.

## 33.2 Blast-radius metrics

- files/modules/owners affected by local changes;
- compile/test fanout;
- ABI consumers affected;
- surprising impact reports.

## 33.3 Architecture health

- number/age of exceptions;
- repeated illegal edge attempts;
- duplicate live resource/socket implementations;
- public API growth/churn;
- module co-change patterns;
- over-fragmented modules that always change together.

## 33.4 Debug/quality metrics

- mean time to identify state/resource owner;
- mean time to diagnose duplicated/stale resource;
- fixture coverage of important product states;
- qualification-suite failures caught before upgrade rollout.

## 33.5 Reuse metrics

Measure meaningful reuse separately:

- Cell reuse across surfaces;
- pure UI reuse;
- Capability consumers.

Do not optimize for the raw number of shared modules.

---

# 37. External architecture and ecosystem influences explored


These influenced reasoning but are not dependencies or requirements.

## 37.1 Slack Circuit

Influence:

- presenter/state production model;
- SubCircuit/embedded composition ideas;
- code generation/golden paths.

Not selected as default runtime because current ViewModel + qualified keyed owner meets requirements with lower framework commitment.

## 37.2 Square Workflow

Influence:

- typed parent/child composition;
- explicit state machines;
- identity/lifecycle thinking.

Not selected because its ceremony/runtime model is heavier than the common product path needs.

## 37.3 Decompose

Influence:

- component identity;
- child lifecycle;
- state retention;
- navigation models;
- alternative if product topology becomes component-tree dominated.

## 37.4 Uber RIBs / Airbnb Trio/Ghost / Spotify Hubs / Yelp Bento/CHAOS / DoorDash Mosaic / Zalando AppCraft

Influence:

- autonomous/product-aligned component boundaries;
- screen composition;
- plugin/section models;
- server-driven composition as a product-economics dial;
- importance of scale-specific tooling.

Helix does not copy any one framework. It adopts the repeated ownership lessons while keeping a KMP-native/simple baseline.

## 37.5 Clean Architecture

Retained value:

- dependency direction;
- product semantics separated from mechanisms.

Rejected failure mode:

- scattering one cohesive feature across many layer folders/modules merely to satisfy taxonomy.

## 37.6 CQRS

Retained lightly:

- Query vs Command semantics;
- intent-shaped mutations.

Rejected:

- separate buses/databases/enterprise machinery for every client interaction.

## 37.7 Actor-model ownership

Retained as a rule:

- one authoritative owner serializes expensive mutable shared resources.

No requirement to implement actors everywhere.

## 37.8 Backstage / internal developer platforms

Influence:

- templates/golden paths;
- platform creates a valid skeleton;
- developer supplies intent rather than reconstructing architecture.

## 37.9 OpenRewrite / structured codemods

Influence:

- versioned recipes;
- deterministic migrations;
- architecture evolution as executable transformation instead of wiki instructions.

## 37.10 Nx-style graph thinking

Influence:

- architecture as graph with derived multidimensional attributes;
- affected verification;
- impact/context services.

Helix avoids mandatory duplicate tag/YAML metadata when Gradle/source/ownership already contain the facts.

---

# 38. Public reference URLs used for qualification/reasoning


These URLs are evidence references, not architectural dependencies.

- AndroidX Navigation 3 releases: `https://developer.android.com/jetpack/androidx/releases/navigation3`
- AndroidX stable channel: `https://developer.android.com/jetpack/androidx/versions/stable-channel`
- Compose Multiplatform Navigation 3: `https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html`
- Compose Multiplatform 1.10 announcement: `https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/`
- AndroidX Compose Runtime releases / retain history: `https://developer.android.com/jetpack/androidx/releases/compose-runtime`
- AndroidX Compose Runtime retain API / RetainedValuesStore: `https://developer.android.com/reference/kotlin/androidx/compose/runtime/retain/package-summary`
- SQLDelight 2.2.1 Web Worker driver: `https://sqldelight.github.io/sqldelight/2.2.1/js_sqlite/`
- Mobile Native Foundation Store releases: `https://github.com/MobileNativeFoundation/Store/releases`
- Koin compiler plugin: `https://github.com/InsertKoinIO/koin-compiler-plugin`
- Koin Compose ViewModel docs: `https://insert-koin.io/docs/reference/koin-compose/compose-viewmodel/`
- Koin common `koinViewModel` source: `https://github.com/InsertKoinIO/koin/blob/main/projects/compose/koin-compose-viewmodel/src/commonMain/kotlin/org/koin/compose/viewmodel/ViewModel.kt`
- AndroidX Lifecycle Compose API (`collectAsStateWithLifecycle`): `https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary`
- OpenTelemetry Kotlin: `https://opentelemetry.io/docs/languages/kotlin/`
- Apollo Kotlin platform matrix (historical GraphQL evaluation): `https://github.com/apollographql/apollo-kotlin/blob/main/docs/source/index.mdx`
- Apollo normalized cache docs: `https://www.apollographql.com/docs/kotlin/caching/normalized-cache`
- Decompose: `https://arkivanov.github.io/Decompose/`
- Slack Circuit: `https://slackhq.github.io/circuit/`
- historical Store5 POC docs: `https://store.mobilenativefoundation.org/`
- Android offline-first data layer guidance (local-first reads, sync as one suspend function, refresh in an external scope): `https://developer.android.com/topic/architecture/data-layer/offline-first`
- Konsist: `https://docs.konsist.lemonappdev.com/`
- OpenRewrite: `https://docs.openrewrite.org/`
- Android modularization guidance: `https://developer.android.com/topic/modularization`
- Sitecore Helix naming/collision context: `https://helix.sitecore.com/`
