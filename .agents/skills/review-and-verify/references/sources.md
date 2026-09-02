# Where the rules for this workflow come from

Master source: `docs/architecture/helix-kmp-source-of-truth.md`.

| Question | Section |
| --- | --- |
| What each enforcement rule means and why it exists | 21. Mechanical architecture enforcement |
| Verification tiers and what each is allowed to prove | 20. Testing and quality architecture |
| What the control plane will eventually verify | 22. The Helix KMP control plane |
| When an ADR or a qualification snapshot is required | 24. ADR and qualification governance |
| Evidence ledger and qualification snapshots | 26. Qualification snapshot and evidence ledger |
| Honest limits of the architecture and its checks | 27. Known costs, risks, and honest counterpoints |

In this repository: the tiers are `./gradlew verifyFast` and `./gradlew verifyFull` (defined in the
root `build.gradle.kts`), the policy is `config/helix/dependency-policy.json`, and the exception
registry is `config/helix/exceptions.json`.
