# Where the rules for this workflow come from

Master source: `docs/architecture/helix-kmp-source-of-truth.md`. Read the section, do not
paraphrase it from memory.

| Question | Section |
| --- | --- |
| What a Feature, Screen, Cell, UI and Capability actually are | 7. Canonical vocabulary |
| Which module role to create, and where it lives | 8. Physical module taxonomy |
| Which dependency edges are legal | 9. Dependency direction |
| When to extract rather than duplicate | 10. Reuse and extraction model |
| Cell / ViewModel / State / Action / UiCommand / Output shapes | 11. Presentation architecture |
| Instance identity and who owns state for how long | 12. Runtime ownership and identity |
| Designing grouped Queries and intent Commands | 13. Capability API and business model |
| Fixtures, owner-seam tests, what to test where | 20. Testing and quality architecture |
| A complete worked slice | `docs/architecture/reference-slice.md` (moved out of Section 30; 30.4 stays in the master) |

In this repository: `:feature:posts` plus `:capability:posts-api` / `:capability:posts-impl` are
the reference slice, and `config/helix/dependency-policy.json` is the authority Section 9 explains.
