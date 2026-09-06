# Helix KMP AGENTS.md and Skill templates

These are the copyable source templates for a repository's root agent instructions and its four
workflow Skills. This repository's live copies are [`AGENTS.md`](../../../AGENTS.md) and
[`.agents/skills/`](../../../.agents/skills/), and they are the ones that run.
[`helix-kmp-source-of-truth.md`](../helix-kmp-source-of-truth.md) remains the normative source;
section 22 defines the shared Skill contract.

---

# Appendix A. Full root AGENTS.md template


**Post-adoption/P1-complete copyable template.** Its law/vocabulary/prohibition fragments are specified from canonical sections and Section 9 policy. Once the P1 generator/no-drift checker exists, repository `AGENTS.md` must be produced or checked from those sources. During P0, use phase-aware instructions that omit unavailable P1 commands.

```markdown
# Helix KMP Agent Instructions

## Architecture

This repository follows **Helix KMP**.

Four laws:

1. Explicit Ownership
2. Product-Facing Boundaries
3. Independent Lifetimes
4. Enforced Evolution

Ownership card:

One state       -> one owner
One resource    -> one owner
One instance    -> one identity
One arch fact   -> one source of truth

## Canonical vocabulary

- Feature = product presentation scope.
- Screen = navigable presentation destination.
- Cell = independently hostable stateful presentation unit owned by a Feature; not a base class or Gradle module.
- UI = stateless reusable rendering.
- Capability = reusable business/product Queries + Commands + stable models.
- Resource = Snapshot, Live, or Projection owned below presentation.

## Required workflow

Prefer workflow Skills and the public CLI over hand-created architecture wiring.

helix-kmp context <target>
helix-kmp create ... --dry-run --explain
helix-kmp verify --fast --affected
helix-kmp impact <target>

If a rule fails:

helix-kmp doctor <scope> --explain

Do not bypass the rule by importing implementation types or adding a global manager/service locator.

## High-value prohibitions

- Feature -> Capability Impl: forbidden.
- Capability Impl -> another business Capability Impl: forbidden.
- UI -> ViewModel/Koin/navigation/Capability/repository: forbidden.
- Capability API -> Compose/network/DB/resource runtime/impl: forbidden.
- Peer Cell implementation -> peer Cell implementation: forbidden.
- UiCommand must not carry correctness-bearing domain/resource state.
- RouteKey != FeatureInstanceKey != ResourceKey.

## Protected work

Escalate for explicit experienced review when changing auth, payments, privacy/security, irreversible migrations, concurrency/shared-resource lifecycle infrastructure, public capability APIs, or architecture/control-plane rules.

## Verification

The change is not complete until the repository's standard Helix verification command is green and required behavioral/visual/impact evidence is available.
```

---

# Appendix B. Full workflow Skill templates


**Single copyable Skill-template specification.** Section 22 defines the shared contract. Once the architecture-kit generator/checker exists, these bodies must be generated/no-drift checked rather than independently edited.

## B.1 `build-feature`

```markdown
# build-feature

Use this workflow for normal product implementation inside an existing Helix boundary or when creating a Screen/Cell/UI/Capability consumer.

1. Classify the lightest shape: simple UI, Cell, Screen, or complex workflow.
2. Run `helix-kmp context <target>`.
3. If creating a unit, run `helix-kmp create ... --dry-run --explain` before applying.
4. Keep presentation dependent on Capability APIs/product models only.
5. Add/update fixtures and owner-boundary tests.
6. Run `helix-kmp verify --fast --affected`.
7. Run `helix-kmp impact <target>` for non-trivial changes.
8. Return evidence: owner changed, tests/checks, visual fixture evidence if applicable, impact summary, exceptions/risk.

Stop and use `evolve-architecture` or ask an architecture owner if the task needs a new module role, Feature -> Impl dependency, peer Cell coupling, public API widening solely for convenience, or broad boundary movement.
```

## B.2 `evolve-architecture`

```markdown
# evolve-architecture

Use this workflow when reuse/ownership/build/context evidence suggests extracting, merging, splitting, or migrating an architecture boundary.

1. Run `helix-kmp graph` and `helix-kmp impact <scope>`.
2. Run `helix-kmp doctor <scope> --explain --json`.
3. Check relevant ADR `Revisit when` conditions.
4. Prefer an approved deterministic recipe.
5. Run `helix-kmp extract|migrate ... --dry-run --explain`.
6. Obtain human approval for the architecture boundary change.
7. Apply the recipe with `--verify`.
8. Ensure generator/rules/master/derived docs/architecture-kit version are updated if the convention itself changed.

Report consumers, ownership, ABI/build/test fanout, context surface, co-change/exception pressure, and expected blast-radius improvement.
```

## B.3 `debug-and-repair`

```markdown
# debug-and-repair

Use for lifecycle/state loss, duplicated live resources, refresh issues, DI failures, architecture violations, and difficult runtime ownership bugs.

1. Identify RouteKey, FeatureInstanceKey, and ResourceKey involved.
2. Capture Resource Inspector / flight-recorder evidence where available.
3. Run `helix-kmp context <target>` and `helix-kmp doctor <scope> --explain`.
4. Trace Action -> ViewModel -> Capability -> resource -> HTTP/DB/socket -> emission -> State.
5. Repair the smallest owner/boundary that is wrong.
6. Add a regression test at that owner seam.
7. Run `helix-kmp verify --fast --affected` and impact analysis.

Never fix a lifecycle/resource bug by moving shared truth into a Screen ViewModel, opening a socket per Cell, or weakening an architecture rule.
```

## B.4 `review-and-verify`

```markdown
# review-and-verify

Use to review a proposed change, generate evidence, or prepare CI/release verification.

1. Run `helix-kmp impact <target-or-diff> --json` where supported.
2. Run `helix-kmp verify --fast --affected`; run `helix-kmp verify --full` for platform/runtime/qualification seams or release-level review.
3. Inspect architecture graph changes and public ABI changes.
4. Check fixtures/goldens for UI changes and behavioral/resource tests independently.
5. Check qualification suites when a version-sensitive seam is touched.
6. Check exception registry additions/expiry.
7. Summarize expected vs actual blast radius and required owners.

A green visual diff cannot override a behavioral/architecture failure. A green unit test cannot justify an illegal dependency edge.
```
