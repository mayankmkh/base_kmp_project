---
name: evolve-architecture
description: Extracting, merging, splitting or migrating a Helix architecture boundary. Use when reuse, ownership or build evidence says the current module boundaries are wrong - not for work that fits inside them.
---

# evolve-architecture

Use this workflow when evidence about reuse, ownership, build cost or context size says a boundary
is in the wrong place, and the change is to the architecture itself rather than inside it.

## What this repository can and cannot do for you

`helix-kmp graph`, `impact`, `doctor`, `extract` and `migrate` are P1/P2 stages of the control
plane and **are not built** (see `AGENTS.md`). There is no deterministic recipe engine and no
`--dry-run --explain` for a boundary move. Every step below is manual and reviewed by a person.

## Steps

1. **Establish the current graph.** Run `./gradlew checkModuleGraph` and read
   `build/reports/helix/module-graph.json`. It is the authoritative edge list.

2. **Establish the blast radius by hand.** Find the consumers of every type you intend to move,
   and note which modules would be recompiled. Record it: this is the "expected" half of the
   before/after comparison you owe the reviewer.

3. **Check the ADRs.** `docs/architecture/adr/` records what was decided and what would justify
   revisiting it. A boundary move that contradicts an accepted ADR needs a new ADR, not a
   workaround.

4. **Check the exception registry.** `config/helix/exceptions.json` plus
   `docs/architecture/exceptions.md`. Repeated exceptions against the same rule are the usual
   evidence that a boundary, not a rule, is wrong.

5. **Prefer the smallest legal shape.** Extracting stateless rendering into `ui/*` or a grouped
   read surface into `capability/<name>-api` is almost always better than widening a rule. If a
   new module is the answer, scaffold it with
   `tooling/helix-kmp/helix-kmp create capability <name>` and move code into it, rather than
   hand-writing the module.

6. **Get human approval before applying.** A boundary change is protected work.

7. **Apply, then verify the whole repository, not just the affected set:**

   ```bash
   ./gradlew verifyFast
   ./gradlew verifyFull   # if a platform, runtime or packaging seam moved
   ```

8. **Update the sources of truth that the change invalidates:** the dependency policy in
   `config/helix/dependency-policy.json`, the role/path tables in
   `docs/architecture/helix-adoption-plan.md`, `build-logic/README.md` if a convention plugin
   changed, the module map in `README.md`, and the CLI templates in `tooling/helix-kmp/templates/`
   if the shape they scaffold is no longer the shape you want. After touching a template, run
   `tooling/helix-kmp/tests/run-tests.sh`.

## Report

Consumers affected, ownership before and after, ABI/build/test fanout, exception pressure removed,
and expected versus actual blast radius.
