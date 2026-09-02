# Architecture exceptions

An exception is a dated, owned, temporary permission to break one Helix rule in one place. It is a
registry entry, not a comment, not a `@Suppress`, and not a quiet edit to
`config/helix/dependency-policy.json`. Changing the policy changes the architecture for everyone;
an exception admits that one place is wrong and says when it will stop being wrong.

The registry is [`config/helix/exceptions.json`](../../config/helix/exceptions.json). It is
currently empty, and that is the preferred state.

## Schema

```json
{
  "schema": 1,
  "exceptions": [
    {
      "rule": "DEP-ROLE-DENIED",
      "scope": ":feature:posts",
      "owner": "mayankmkh@gmail.com",
      "reason": "one sentence saying why the correct shape is not available yet",
      "created": "2026-09-02",
      "expires": "2026-12-01",
      "removalCondition": "what has to become true for this entry to be deleted"
    }
  ]
}
```

| Field | Required by `checkModuleGraph` | Meaning |
| --- | --- | --- |
| `rule` | yes | The rule ID being excepted, exactly as it appears in the finding |
| `scope` | yes | The finding's subject, exactly -- usually a Gradle path such as `:feature:posts` |
| `owner` | yes | A person, not a team alias. Somebody has to be asked about it |
| `reason` | yes | Why the correct shape is unavailable *now* |
| `expires` | yes | ISO date. After it, the build fails with `EXC-EXPIRED` |
| `created` | no | Recorded for humans; the task ignores it |
| `removalCondition` | no | Recorded for humans; the task ignores it. Write it anyway -- an entry with no stated exit is a permanent rule change in disguise |

`rule` and `scope` must match the finding *exactly*. A finding is printed as
`[RULE-ID] subject — problem. Fix: remedy`; `rule` is the bracketed ID and `scope` is the subject.
A mismatch does not error, it simply fails to suppress anything.

## What an active exception does

`./gradlew checkModuleGraph` downgrades a matched finding from an error to a warning and appends
the reason, owner and expiry to the printed line. The finding stays in
`build/reports/helix/module-graph.json` with `"severity": "warning"`, so an excepted violation is
still visible to any review that reads the report. Nothing is hidden; the build is merely allowed
to go green.

## Adding one

1. Try not to. First check whether the right answer is a different boundary -- extracting to
   `ui/*`, adding a grouped read surface to a `capability/*-api`, or moving the state to the owner
   that should have had it. Use the `evolve-architecture` skill.
2. Run `./gradlew checkModuleGraph` and copy the rule ID and subject out of the finding verbatim.
3. Add the entry with a real owner, a real reason, an expiry you actually believe (weeks, not
   years), and a removal condition.
4. Re-run `./gradlew checkModuleGraph`. The finding must now be a warning naming your entry. If it
   is still an error, `rule` or `scope` does not match.
5. Say in the change description that you added an exception, and to which rule.

## Expiring one

On the day after `expires`, `checkModuleGraph` emits `EXC-EXPIRED` against the entry's scope and
the build fails. There are exactly two legitimate responses:

- **Remove it** -- delete the entry, because the underlying violation is gone. Verify with
  `./gradlew checkModuleGraph`.
- **Renew it** -- with the owner's approval, set a new `expires` and a new `removalCondition` that
  is different from the one that just failed. Renewing with the same condition is how a temporary
  exception becomes permanent.

Deleting an entry that is still needed, so that a real violation becomes an error again, is also a
legitimate outcome: it turns an invisible deadline into a visible failure.

## Related

- Rule IDs and what each one means: [`helix-adoption-plan.md`](helix-adoption-plan.md)
- The dependency matrix itself: [`config/helix/dependency-policy.json`](../../config/helix/dependency-policy.json)
- Governance model in full: master source, Section 24
