# ADR 0002: A `platform_view` role for hosting platform views in Compose

- **Status: proposed, not adopted.** Nothing in the policy file, the convention plugins, the role
  enum, the templates or the CLI implements this. It is written down so the next platform-backed
  component is a decision rather than a rediscovery.
- **Date:** 2026-09-10
- **Owner:** mayankmkh@gmail.com
- **Depends on:** [`0001-helix-adoption.md`](0001-helix-adoption.md), whose role-per-module premise
  this extends rather than changes

## Decision

Proposed: add a **`platform_view`** role for the Compose boundary that hosts a platform view.

- policy key `platform_view`, path `:platform-view:*`, plugin id `bkp.kmp.platform.view`;
- it may depend on `platform`, `platform_api` and `foundation_api`;
- `feature` and `app` may depend on it;
- `ui` and `capability_impl` may **not**, which preserves the invariant that keeps Compose out of
  capability implementations and keeps `:ui:*` previewable;
- it carries no ViewModel convention, because the state belongs to the engine (source of truth
  §12.15). Its Composable takes its data and callbacks as parameters.

**Scope.** A module in this role holds only the Composable that creates, hosts and disposes a
platform view or rendering surface and connects it to an engine. The review test is one line:

```text
Compose draws the pixels        -> :ui:*
a platform view draws them      -> :platform-view:*
```

Controls, chrome, overlays, placeholders and error states are Compose-drawn and belong in `:ui:*`,
composed over the surface by the `feature` that assembles the screen.

**Companion rule.** `PLATFORM-VIEW-WITHOUT-PLATFORM`: a `:platform-view:*` module that declares no
dependency on a `:platform:*` module is a `checkModuleGraph` finding. Without it the role is a
fuzzy invitation; with it, the role's one justification is enforced.

Adopt when the **second** platform-backed component appears. Until then, host such a component in
`feature` and link this ADR from its module.

## Context / problem

A video player, a map and a webview have the same shape: a Compose surface bound to a platform
engine whose state the engine owns. The Compose half needs Compose; the engine half needs
`platform`, and on Android usually a vendor SDK.

The matrix (`config/helix/dependency-policy.json`) has no role for that combination:

- `ui` may depend on `ui` and `foundation_api` only, so it cannot reach `platform`;
- `platform` may depend on `foundation_api` and `foundation_runtime` only, so it cannot reach `ui`;
- `capability_impl` may reach `platform` but not `ui`, and it is a data role in any case.

That leaves exactly one role that can hold both halves: `feature`. But `feature` means "product
presentation scope" (§8.2), and a reusable player is not a product surface. Worse, `feature`
carries the Cell and ViewModel conventions of §11 and §30.4, so a component that owns no
presentation state arrives where owning presentation state is the default. That is how the
media-player discussion reached a ViewModel it did not need: the placement was forced by the
matrix, then read back as an instruction.

This is the red flag §35 now names: a module carrying a role whose responsibilities do not describe
it.

## Why

**Why the role.** It is the only way to state what the component is, so that no reader has to be
told the placement was forced.

**Why it may not depend on `ui`.** The allow list is how the scope rule is enforced rather than
merely written down. Controls need the design system; a module that cannot reach `:ui:*` cannot
draw a themed scrubber, so the boundary holds without relying on review. Denying the `ui` role does
not deny Compose: the plugin applies Compose, and `Modifier`, layout, `MaterialTheme` and
`rememberSaveable` are external artifacts, not project modules. Nearly everything a surface
plausibly wants is therefore still in reach: theme colours for a webview background or a dark map
style come from `MaterialTheme`, aspect ratio and corner shape come from Compose modifiers, and
loading and error states leave as state or callbacks for the caller to draw. The remaining case, a
project design-system component rendered inside the surface module, is exactly what the rule
exists to prevent. If a genuine need appears, widening is one policy entry with evidence behind it,
whereas narrowing later is a migration; default-deny is the matrix's whole design, and both the
conditional-allow mechanism and the expiring exception registry are documented ways to revisit it.

**Why defer adoption.** The cost is not the role, it is the enforcement surface behind it:
`HelixRole`, a convention plugin, a path pattern, the policy file, the docs, the templates and the
CLI all learn a new word, and every one of them is a place the word can go stale. One module in a
slightly wrong role, with an ADR link explaining why, is cheaper and more honest than a role that
exists for one client. Promotion later is mechanical: move the directory, swap the plugin, update
the `include(...)` entry and the project accessors. No source has to change.

## Alternatives considered

**Keep hosting these in `feature`.** What to do today, and what this ADR recommends until the
second component appears. The cost is the failure this ADR names: the module inherits Cell and
ViewModel conventions it does not need, and a reader has to be told so. Mitigation is a link to
this ADR from the module and a KDoc line saying the component owns no presentation state.

**Let `ui` depend on `platform`.** The one-line change to the policy file. Rejected on two counts.
First, `platform` may depend on `foundation_runtime`, so allowing it opens a transitive route from
`ui` into runtime infrastructure that the `ui` row exists to forbid; the rule would still read as
denied while no longer being enforced. Second, it makes every `:ui:*` module potentially
non-previewable, because a preview would be one import away from needing a platform engine.

**An opaque handoff type in a `foundation_api` module.** Define the engine handle below
presentation, let `ui` render against the opaque type and let `platform` produce it. Rejected: with
`media3-ui-compose` the Android `actual` has to be a vendor `Player` type, because that is what the
vendor's Composable consumes. That puts a vendor SDK type on a `foundation_api` surface, the one
place that must stay free of vendor and product vocabulary. The abstraction would be opaque in name
only.

**Split the modules but leave them under `feature`.** A non-answer: it splits without giving them a
role, so `MOD-PATH-ROLE-MISMATCH` fails and the split has to be bought back with an exception.

### Names considered

- **`widget`**, path `:widget:*`. Rejected: UI vocabulary that under-specifies the one thing
  justifying the role, so it would attract pure-Compose modules that belong in `:ui:*`.
- **`platform_ui` nested as `:platform:*-ui`.** Rejected: it puts Compose inside the `platform/`
  tree, costing the invariant that nothing under `platform/` knows Compose, which is a heuristic a
  reviewer can apply without opening the policy file. It would also force the existing
  `PLATFORM` pattern `^:platform:(?!.*-(?:api|impl)$)[^:]+$` to grow a `-ui` exclusion.
- **`platform_ui` as its own tree, `:platform-ui:*`.** The runner-up, and mechanically clean: it
  keeps the `platform/` invariant and needs no change to the `PLATFORM` pattern. Rejected on
  reading only: "the UI half of a platform feature" invites controls and chrome, which is the scope
  error this role must not attract. `platform_view` names what is inside it.

## Consequences / costs

**If adopted**, these change together, and none may lag:

- a `PLATFORM_VIEW("platform_view", "bkp.kmp.platform.view", Regex("^:platform-view:[^:]+$"))`
  entry in `HelixRole`. The existing `PLATFORM` pattern needs no change, because `:platform-view:*`
  does not match `:platform:`;
- a `bkp.kmp.platform.view` convention plugin applying Compose and the role base;
- a `platform_view` key in `config/helix/dependency-policy.json` allowing `platform`,
  `platform_api` and `foundation_api`, plus `platform_view` added to the `allow` lists of `feature`
  and `app`;
- `PLATFORM-VIEW-WITHOUT-PLATFORM` in `CheckModuleGraphTask` and in the rule table;
- a `create platform-view <name>` verb with its own template tree, and a `run-tests.sh` case;
- rows in §8.2 and §9.1, in `build-logic/README.md`, and in the `ROLE_METADATA` table that
  `helix-kmp verify --agents` renders into `AGENTS.md`.

**Good**

- the role says what the module is, so no reader has to be told the placement was forced;
- `feature` keeps meaning "product presentation scope", which is what makes its ViewModel and Cell
  conventions safe as defaults;
- the `ui` row stays intact, so `:ui:*` stays previewable and out of runtime infrastructure.

**Costs, honestly**

- one more role in a taxonomy whose value comes partly from being small;
- another convention plugin to keep in step on every Compose or target change;
- a boundary a reviewer has to hold in the one place the compiler cannot explain it: whether a
  given Composable draws pixels or hosts something that does.

## Qualification evidence

None yet, which is why this is proposed rather than accepted. Adoption needs:

- two real platform-backed components in the repository, not one plus a hypothesis;
- a `checkModuleGraph` run proving `platform_view -> ui`, `ui -> platform_view` and
  `capability_impl -> platform_view` are denied, `feature -> platform_view` is allowed, and
  `PLATFORM-VIEW-WITHOUT-PLATFORM` fires on a module with no engine dependency;
- one qualification pass per target for the second component, since the point of the role is that
  the engine half is platform-specific.

## Revisit when

- a second platform-backed component appears: that is the trigger, and the decision rule above is
  the content of this ADR;
- a real case needs a `:ui:*` dependency from this role. Bring the case, not the principle: the
  answer is a conditional allow or an expiring exception with the component named, not a widened
  row;
- an exception is opened to let some module reach `platform` from `ui`, which is evidence the gap
  described here stopped being absorbed by hosting in `feature`;
- `media3-ui-compose` or an equivalent stops requiring a vendor type at the Compose boundary, which
  would revive the rejected `foundation_api` handoff.

## Migration path

**Into the role**, per module: `git mv feature/<name> platform-view/<name>`, swap the role plugin,
rename the `include(...)` entry, update the project accessor at each call site, and run
`./gradlew checkModuleGraph`. The Kotlin package may stay as it is; nothing in the matrix reads it.

**Out of the role**, if it proves wrong: the reverse move plus a plugin swap, and the additions
listed above run backwards. Nothing durable, persisted or public depends on a module's role, so
neither direction is a data migration.
