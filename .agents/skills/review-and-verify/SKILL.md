---
name: review-and-verify
description: Reviewing a proposed change against the Helix rules and producing verification evidence for it, including release-level checks. Use when reviewing a diff or preparing evidence rather than writing the change.
---

# review-and-verify

Use this workflow to review a proposed change, produce evidence for it, or prepare release-level
verification.

## Steps

1. **Establish the blast radius.** `helix-kmp impact` is P1 and not built, so read the diff: which
   modules changed, and which modules depend on them according to
   `build/reports/helix/module-graph.json` (written by `./gradlew checkModuleGraph`).

2. **Run the tier that matches the radius.**

   ```bash
   tooling/helix-kmp/helix-kmp verify --fast --affected   # a change inside a few modules
   ./gradlew verifyFast                                   # build-logic, settings or config changed
   ./gradlew verifyFull                                   # platform, runtime or packaging seams, and releases
   ```

   `verify --fast --affected` falls back to the whole fast tier by itself when `build-logic/`,
   `settings.gradle.kts`, `gradle/`, `config/helix/` or `config/detekt/` changed, because the
   affected set is not trustworthy then. Never run a bare `./gradlew build`.

3. **Inspect the graph and the public surface.** Did an edge appear that was not there before? Did
   a `capability/*-api` or a Feature's `api` package gain a declaration? A widened public surface
   is a design decision and needs a reason in the change description.

4. **Check the tests independently of the visuals.** Behavioural and resource tests must pass on
   their own; a matching screenshot does not stand in for them.

5. **Check the exception registry.** Any new entry in `config/helix/exceptions.json` needs an
   owner, a reason, an expiry date and a removal condition -- see
   `docs/architecture/exceptions.md`. An expired entry fails `checkModuleGraph` with `EXC-EXPIRED`
   and must be removed or renewed deliberately, not extended silently.

6. **If the change touched the control plane** (`tooling/helix-kmp/**`), require its own test to
   have been run:

   ```bash
   tooling/helix-kmp/tests/run-tests.sh
   ```

7. **Summarise** expected versus actual blast radius, which owners must sign off, and what is not
   covered by any check.

## Rules of judgement

A green visual diff cannot override a behavioural or architecture failure. A green unit test cannot
justify an illegal dependency edge. "It compiles" is not evidence about ownership.
