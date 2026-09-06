# ADR 0001: Adopt Helix KMP as this repository's architecture

- **Status:** Accepted
- **Date:** 2026-09-02
- **Owner:** mayankmkh@gmail.com
- **Supersedes:** the ad-hoc `shared/libs/*` layout the template started from

## Context

This repository is a Kotlin Multiplatform starter meant to be copied into real products and worked
on by both people and AI agents. Before this decision it had a conventional layout: a `shared`
module with `libs/*` subprojects, screen-scoped ViewModels, and architecture rules that existed
only in whoever had last reviewed a change.

That shape fails in three specific ways, all of which the master source
([`../helix-kmp-source-of-truth.md`](../helix-kmp-source-of-truth.md), Section 4) documents in
detail:

- **State ownership is accidental.** State survives a configuration change because it happens to
  sit in a screen ViewModel, not because anybody decided that screen owns it. Showing the same
  widget twice collides on identity.
- **Reuse duplicates resources.** Two placements of the same stateful component open two
  connections, or grow ad-hoc batching to avoid it.
- **The rules are unenforceable.** An architecture that lives in review comments is one busy week
  away from not existing, and an AI agent given a large ambiguous codebase will confidently
  amplify whatever pattern it finds first.

The last point is what made this urgent rather than tidy. Agents need a boundary they cannot
accidentally cross and a verification command whose result is unambiguous.

## Decision

Adopt **Helix KMP** as the architecture of this repository, in the form specified by the master
source, with mechanical enforcement from day one.

Concretely:

1. **Roles are physical.** Every Gradle module has exactly one role, declared by applying exactly
   one `bkp.kmp.*` convention plugin, and its path must agree with its role. The module map is in
   [`../../../README.md`](../../../README.md); the role/plugin/path mapping is in
   [`../helix-adoption-plan.md`](../helix-adoption-plan.md).
2. **The dependency matrix is data, not prose.** [`config/helix/dependency-policy.json`](../../../config/helix/dependency-policy.json)
   is the single source; `./gradlew checkModuleGraph` enforces it and `checkHelixPolicySync` keeps
   the plugin's own view of it honest. Findings are formatted `[RULE-ID] subject -- problem. Fix: remedy`.
3. **Presentation is Cells, not screens.** A Cell is `(id, instanceKey: FeatureInstanceKey, onOutput)`.
   Instance identity is explicit, so two placements of the same Cell never share state by accident,
   and a Feature's only way out is an Output the host interprets.
4. **Capabilities own data.** Grouped `Queries` and intent `Commands` in `capability/*-api`; the
   implementation in `capability/*-impl` is `internal` except for its Koin module. A Feature may
   depend on the API and never on the implementation.
5. **Exceptions are dated registry entries**, not comments -- see [`../exceptions.md`](../exceptions.md).
6. **Verification is tiered and named:** `verifyFast` for the inner loop, `verifyFull` for
   platform seams and releases.
7. **The control plane is staged.** Stage P0 -- `helix-kmp create` and `helix-kmp verify` -- is
   built and lives in `tooling/helix-kmp/`. Stage P1 adds thin `graph`, `impact`, `doctor`,
   `context`, and gallery-index commands plus generated/no-drift agent instructions. P2
   (`extract`, `migrate`) remains deliberately unbuilt.

## Alternatives considered

**Keep the conventional KMP screen architecture.** Cheapest, and the failure mode is well known:
God ViewModels per screen, state that survives by accident, and rules that decay. Rejected because
it does not survive contact with either component reuse or AI agents (master source, Sections 4 and
5.1).

**AI-First Modular KMP Blueprint (heavier module split, no Cell concept).** Improves blast radius
but leaves presentation state ownership implicit, so the identity and duplicate-resource problems
survive the split. Rejected (Section 5.2).

**Decompose for component lifecycle and navigation.** Genuinely solves component identity and
would have given Cells a lifecycle model for free. Rejected in favour of Navigation 3 plus explicit
`FeatureInstanceKey`, to stay on the AndroidX-shaped path the rest of the template already uses.
This reverses an earlier decision recorded in the exploration history (Section 5.3); Helix does not
depend on which runtime won, because identity is explicit either way.

**GraphQL / Relay / Apollo-style declarative data.** A strong fit for the Capability layer's
grouped read surface. Deferred, not rejected: it presumes a server contract this template does not
have (Section 5.8).

**Server-driven UI.** Rejected for a starter template: it moves the architecture problem to a
server that does not exist here (Section 5.9).

**Lean modular monolith -- keep `shared/*`, add lint rules.** The cheap version of this decision.
Rejected because role-per-module is what makes the rules mechanically checkable; without physical
roles, `checkModuleGraph` has nothing to check (Section 5.10).

**A single global application store.** Rejected: one store is the opposite of one-owner-per-state,
and it makes blast radius global by construction (Section 5.11).

## Consequences

**Good**

- Illegal dependencies fail the build with a rule ID and a suggested fix, in seconds, without a
  reviewer.
- Blast radius is legible: the module graph report says who depends on what.
- An agent can be given a small, named boundary and a verification command, which is the whole
  premise of `AGENTS.md` and `.agents/skills/`.
- Adding a Feature, Capability or Cell is a command rather than a copy-paste, so new modules start
  compliant instead of being made compliant later.

**Costs, honestly**

- More modules, and Gradle configuration time grows with them.
- More ceremony for small work: a Cell needs a ViewModel, a Content, an Output and a Koin
  registration even when it shows one string.
- The rules can be wrong. When they are, the answer is an ADR and a boundary change, which is
  slower than editing the policy file -- deliberately.
- The stable control-plane wrapper is Bash 3.2, with thin Python 3 P1 readers and hand-written
  scaffolding templates. The tooling self-test covers both surfaces.
- Sophisticated doctor scoring, advanced context ranking, and refactoring recipes remain P2.

## Revisit when

- Exception pressure concentrates on one rule -- that is evidence about the rule, not the code.
- Configuration time becomes the dominant cost of the inner loop.
- Product history justifies P2 ranking or deterministic extraction/migration recipes.
