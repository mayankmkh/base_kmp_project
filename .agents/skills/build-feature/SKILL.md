---
name: build-feature
description: Normal product implementation inside an existing Helix boundary - adding or changing a Screen, Cell, UI component or Capability consumer. Use when the work fits the boundaries that already exist.
---

# build-feature

Use this workflow for product implementation inside an existing Helix boundary, or when creating a
Screen, Cell, UI component or Capability consumer.

## Steps

1. **Classify the lightest shape that works.** Stateless rendering -> a `ui/*` Composable. State
   owned per placement -> a Cell. A navigable destination -> a Screen wrapping a Cell. Reusable
   business data -> a Capability. Pick the smallest one; do not create a Feature for a component.

2. **Read the boundary you are about to touch.** There is no `helix-kmp context` here (P1, not
   built), so do it by hand:
   - the module's `build.gradle.kts` -- its role plugin and its dependencies;
   - its `api` package -- everything public is the contract;
   - `./gradlew checkModuleGraph` then `build/reports/helix/module-graph.json` for the current
     edges.

3. **Scaffold with the CLI rather than by hand.** Preview first:

   ```bash
   tooling/helix-kmp/helix-kmp create feature <name> --dry-run
   tooling/helix-kmp/helix-kmp create feature <name> [--capability <name>]
   tooling/helix-kmp/helix-kmp create capability <name>
   tooling/helix-kmp/helix-kmp create cell <feature> <CellName>
   ```

   `create` adds the module to `settings.gradle.kts` and, for a Cell, registers its ViewModel in
   the Feature's Koin module. It never edits the app's composition root: load the new
   `<name>FeatureModule` or `<name>CapabilityModule` in `:app:shared` yourself.

4. **Keep presentation on Capability APIs and product models only.** A Feature depends on
   `capability/<name>-api`, never on `-impl`. Cell state is keyed by `FeatureInstanceKey`, so two
   placements of the same Cell never share it. Outputs are the only way out of a Feature; a
   `UiCommand` carries presentation effects, never correctness-bearing state.

5. **Add or update fixtures and owner-boundary tests.** Fixtures live beside the code they
   describe; shared fakes live in `testkit/*`. Test the ViewModel against the Capability's
   interfaces, never against its implementation. `:feature:posts` is the reference slice.

6. **Verify.**

   ```bash
   tooling/helix-kmp/helix-kmp verify --fast --affected
   ./gradlew <module>:spotlessApply   # if the formatter disagrees
   ```

7. **Report evidence:** which owner changed, which tests and checks ran and their result, visual
   evidence for UI changes, and any exception or risk you introduced.

## Stop and escalate

Switch to `evolve-architecture`, or ask an architecture owner, if the task needs a new module role,
a Feature -> Impl dependency, peer Cell coupling, a public API widened purely for convenience, or
any broad movement of a boundary.
