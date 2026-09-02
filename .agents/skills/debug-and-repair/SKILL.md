---
name: debug-and-repair
description: Diagnosing lifecycle and state-loss bugs, duplicated live resources, refresh problems, DI failures and architecture-rule violations. Use when something behaves wrongly at runtime or a Helix rule fails and the cause is not obvious.
---

# debug-and-repair

Use this workflow for state loss across lifecycle, duplicated live resources, refresh that does not
happen or happens twice, Koin resolution failures, and architecture-rule violations whose cause is
not obvious.

## Steps

1. **Name the three keys.** Most ownership bugs are a confusion between them:
   - the route key -- which destination;
   - the `FeatureInstanceKey` -- which Cell instance owns this state
     (`FeatureInstanceKey.forScreen` / `forPlacement` in `:foundation:presentation`);
   - the resource key -- which underlying resource is being observed.

   If two things that should be separate share a key, or one thing that should be single has two,
   that is the bug.

2. **Load the target context and explain any rule finding.** Findings are formatted
   `[RULE-ID] subject -- problem. Fix: remedy`. The rule IDs and what each means are listed in
   `docs/architecture/helix-adoption-plan.md`.

   ```bash
   tooling/helix-kmp/helix-kmp context <target>
   tooling/helix-kmp/helix-kmp doctor [<scope>] --explain
   ```

3. **Trace the whole path, in order:** Action -> ViewModel -> Capability Queries/Commands ->
   resource -> HTTP/DB/socket -> emission -> State -> Content. Find the first hop where the value
   or the lifetime is already wrong. Do not start repairing before you have found it.

4. **Repair the smallest owner that is wrong.** Fix the owner, not the symptom at the call site.

5. **Add a regression test at that owner's seam** -- the ViewModel against fake Queries/Commands,
   or the Capability implementation against its own contract.

6. **Verify:**

   ```bash
   tooling/helix-kmp/helix-kmp verify --fast --affected
   tooling/helix-kmp/helix-kmp impact <target>
   ```

## Never

Never repair a lifecycle or resource bug by moving shared truth up into a Screen ViewModel, by
opening a connection per Cell, by giving two placements the same instance key so the state
"survives", or by weakening an architecture rule. If the rule is genuinely wrong, that is
`evolve-architecture` plus an ADR, not an edit to the policy file in passing.
