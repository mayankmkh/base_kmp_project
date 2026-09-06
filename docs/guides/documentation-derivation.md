# Documentation derivation rules

This is the guide for authors deriving audience-shaped documents from the master source: the
catalog of technical, management, QA, contribution and adoption documents, and the readability
rules they follow.
[`helix-kmp-source-of-truth.md`](../architecture/helix-kmp-source-of-truth.md) remains the
normative source; subsections keep their original `34.n` numbers.

---


This master is intentionally dense. Derived docs should be audience-shaped.

Every published derived document must be **self-sufficient for its declared purpose and audience**: it must not require this chat thread, old review artifacts, or undocumented oral history. It may link to deeper derived pages for optional detail, but all facts needed to execute its stated job must be restated from this master. Derived documents never cite the conversation as authority.

## 34.1 Canonical derived technical documents

### Architecture Specification

Derive from:

- Sections 1-18;
- normative rules in 21-25;
- qualification summary only, not full history.

Omit most historical alternatives except concise `Why` notes.

### Implementation Guide

Derive from:

- 7-18;
- 30;
- operational parts of 22;
- checklists.

Focus on "what do I do?" not architecture history.

### Rules & Dependency Matrix

Derive from:

- 8-10;
- 21;
- identity/resource restrictions.

Should be compact and machine-oriented.

### Resource & Data Architecture

Derive from:

- 13-18;
- relevant ADRs/known costs.

### Runtime & Ownership Model

Derive from:

- 11-12;
- qualification notes for Nav3/keyed host/Web.

### Golden Path & Control Plane

Derive from:

- 21-24.

### Testing, Fixtures & Observability

Derive from:

- 19-20;
- QA parts of 32.

### ADR & Qualification Register

Derive from:

- 24-28;
- current verified upstream snapshot.

## 34.2 Management / low-tech docs

The full low-tech/cross-functional document family agreed during the documentation design is:

| Derived document | Primary audience | Core job |
|---|---|---|
| **Helix KMP: Executive Overview** | engineering/product leadership, managers, architects | explain the business/engineering problem, investment, outcomes, risks, and success measures |
| **Why Helix KMP: Principles, Trade-offs & Design Philosophy** | everyone | explain ownership, low blast radius, cohesion/coupling, modular reuse, AI/context efficiency, simple defaults, and conscious costs without Kotlin detail |
| **Helix KMP Technology Choices: Why This, Why Not That** | managers, architects, web/backend/mobile engineers | record why current libraries/mechanisms were chosen, alternatives explored, risks, and `Revisit when` conditions |
| **How Software Gets Built in Helix KMP** | PM, QA, design, cross-stack engineers | follow a change from requirement through context/generation/code/fixtures/tests/review/release |
| **Contributing Without Mobile Expertise** | backend/web/data/QA/AI-assisted contributors | show exactly what can be changed safely without Android lifecycle/Gradle/Compose-internals knowledge |
| **Helix KMP Quality & Test Automation Philosophy** | QA, developers, managers | explain layered verification, what each test rail proves, and why goldens are not correctness |
| **QA Guide to Helix KMP** | manual QA and automation QA | show fixture-gallery, resource-inspector, lifecycle/offline/live-resource scenarios, and actionable bug evidence |
| **Safe Change & Blast-Radius Guide** | managers, QA, all engineers | explain expected impact, `impact`/affected verification, surprising fanout, and architecture-health signals |
| **Helix KMP for Backend & Web Engineers** | non-mobile software engineers | map familiar frontend/backend concepts to Cell/Capability/Resource without teaching Android first |
| **Helix KMP Glossary & Mental Model** | everyone | short visual `is / is not` reference for all canonical vocabulary and identities |
| **Architecture Evolution: How Helix Changes Without Rewrites** | leadership, architects, senior engineers | explain `Revisit when`, qualification, doctor, codemods, replacement of libraries, extraction/merge, and retreat paths |
| **Helix KMP Contribution & Ownership Policy** | leads, managers, contributors | define contribution lanes, CODEOWNERS/review zones, exceptions, protected work, and agent/human permission parity |

These are **derived views**, not additional architecture authorities.

Main readability rule:

> Lead with outcome, bullets, diagram, and measures. Put technical detail behind optional `<details>` sections or link to technical docs.

### Executive Overview

Use:

- 1-4;
- 33;
- 27 costs in plain language.

Avoid code/library detail in main path.

### Why Helix KMP

Use:

- four laws;
- design qualities;
- problem diagnosis;
- simplified history/alternatives;
- why AI changes weighting.

### Technology Choices: Why This / Why Not That

Use:

- 18;
- 25-28;
- historical alternatives.

Format as:

```text
choice | why now | risk | why not alternative | revisit when
```

### How Software Gets Built

Use:

- control-plane workflow diagram;
- contribution lanes;
- one simple reference change.

### Safe Change & Blast Radius

Use:

- 3.1;
- impact/doctor;
- metrics.

### Architecture Evolution

Use:

- `Revisit when`;
- doctor/codemods;
- replaceable technology.

## 34.3 QA docs

Primary derived pages are **Helix KMP Quality & Test Automation Philosophy** and **QA Guide to Helix KMP**.

Use:

- 19-20;
- 32;
- selected resource/runtime diagrams.

Keep framework names secondary. Lead with observable states and workflows.

## 34.4 Non-mobile contribution docs

Primary derived pages are **Contributing Without Mobile Expertise**, **Helix KMP for Backend & Web Engineers**, **Helix KMP Glossary & Mental Model**, and **Helix KMP Contribution & Ownership Policy**.

Use:

- 31;
- Skills/context/CLI;
- vocabulary/reuse decision tree;
- protected zones.

Do not teach Android lifecycle unless the contribution lane requires it.

## 34.5 Adoption Playbook

Derive from:

- 29;
- full embedded normative contracts from 2, 7-25;
- current qualification snapshot;
- reference Cricket slice;
- adoption completion checklist.

The adoption document is intentionally self-contained and disposable after conversion.

## 34.6 HTML docs site

Markdown remains source. HTML is the primary human reading surface because it supports:

- navigation;
- search;
- anchors/deep links;
- diagrams;
- responsive layout;
- collapsible optional detail;
- links from CLI error codes.

PDF/DOCX are generated snapshots for formal circulation/sign-off only.

## 34.7 Readability rules for derived non-technical docs

- begin with a 60-90 second summary;
- use short bullets;
- use one simple workflow/graph early;
- define Cell/Capability in plain language;
- avoid long code blocks;
- hide technical implementation in optional details;
- distinguish benefits from costs;
- include measurable outcomes;
- do not present library choices as architecture laws.
