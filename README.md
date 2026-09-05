# base_kmp_project

A Kotlin Multiplatform starter -- Android, iOS, desktop (JVM) and web (wasm) -- built on
**Helix KMP**: one role per module, a dependency matrix that is data rather than prose, and a build
that fails on an illegal edge with a rule ID and a suggested fix.

If you are an agent, start at [`AGENTS.md`](AGENTS.md).

## Layout

Every module has exactly one role, declared by applying exactly one `bkp.kmp.*` convention plugin.
The path and the role must agree; `./gradlew checkModuleGraph` enforces both.

| Module | Role | What it is |
| --- | --- | --- |
| `:app:android` | app | Android application and its composition root |
| `:app:desktop` | app | Desktop (JVM) application entry point |
| `:app:shared` | app | Shared composition root, navigation host and Koin startup; also the iOS framework |
| `:app:web` | app | Wasm browser application entry point |
| `:capability:identity-api` | capability_api | Session state and sign-in/sign-out commands; credentials never cross the API |
| `:capability:identity-impl` | capability_impl | Credential persistence and session ownership; supplies `CredentialProvider` |
| `:capability:posts-api` | capability_api | `PostsQueries`, `PostsCommands`, and the `Post` / `PostFeed` product models |
| `:capability:posts-impl` | capability_impl | Snapshot/network implementation and the posts-owned SQLDelight schema; `internal` but for its Koin module |
| `:feature:posts` | feature | The posts Screens, Cells, ViewModels and Outputs |
| `:foundation:network` | foundation_runtime | Ktor client setup, opt-in auth/retry, request IDs and `NetworkFailure` mapping |
| `:foundation:preferences` | foundation_runtime | Typed preferences and JSON document stores over DataStore |
| `:foundation:presentation` | foundation_api | `FeatureInstanceKey`, `CellPlacementId`, `CellSpec` |
| `:foundation:resource` | foundation_api | `ResourceObservation`, `RefreshOutcome`, refresh QoS, problem taxonomy |
| `:foundation:resource-runtime` | foundation_runtime | Domain-blind sync coordination plus network-result outcome helpers |
| `:foundation:runtime` | foundation_runtime | Application scope, dispatchers, logging |
| `:foundation:sqldelight` | foundation_runtime | Async query flows, the shared `SqlDriverProvider`, and lazy generated databases |
| `:platform:connectivity` | platform | Network-reachability seam, per platform |
| `:platform:secure-storage` | platform | Keystore, Keychain, OS-vaulted desktop and in-memory web secret stores |
| `:storage:database` | storage | Assembles capability schemas, drivers, merged migrations, and the product cache `app.db` |
| `:testkit:common` | testkit | Coroutine, in-memory SQLDelight, and product fixtures -- test code only |
| `:ui:design-system` | ui | Theme and stateless rendering; no ViewModel, no Koin, no navigation |

Plus, outside the runtime graph:

| Path | What it is |
| --- | --- |
| `build-logic/` | The `bkp.*` convention plugins and the architecture rules -- see [`build-logic/README.md`](build-logic/README.md) |
| `tooling/helix-kmp/` | The Helix control plane (stage P1): the CLI, its templates and its tests |
| `config/helix/` | The dependency policy and the exception registry |
| `docs/architecture/` | The master source, the adoption plan, the ADRs |

## Verifying

```bash
tooling/helix-kmp/helix-kmp verify --fast --affected   # touched modules' verifyFastModule lifecycles
./gradlew verifyFast                                   # every module lifecycle, plus root style and graph checks
./gradlew verifyFull                                   # the above, plus Android debug, web bundles, iOS simulator framework
```

Do **not** run a bare `./gradlew build`. It builds fourteen production web bundles, thirteen of
which nothing consumes, and exhausts memory. Use `check` plus the targeted assembles above.

If the formatter disagrees with you, `./gradlew <module>:spotlessApply`.

## Adding a Feature or a Capability

Use the control plane rather than copying a directory:

```bash
tooling/helix-kmp/helix-kmp create feature <name>                      # self-contained Feature
tooling/helix-kmp/helix-kmp create feature <name> --capability <name>  # Feature that reads a Capability
tooling/helix-kmp/helix-kmp create capability <name>                   # <name>-api and <name>-impl
tooling/helix-kmp/helix-kmp create cell <feature> <CellName>           # a Cell inside an existing Feature
```

Add `--dry-run` to see the file list without writing anything. Each command writes the module,
adds it to `settings.gradle.kts`, and loads a new `<name>FeatureModule` or
`<name>CapabilityModule` into the composition root. For a Cell, it registers the ViewModel in the
Feature's Koin module.

For Cells hosted inside a lazy list, derive `KeyedOwnerHost`'s active keys from the viewport with
`rememberViewportKeys`, including its bounded prefetch buffer. Gate periodic refresh in the
ViewModel with `stateIn(SharingStarted.WhileSubscribed(...))` and place the refresh inside the
shared flow so it stops when the item leaves composition and resumes when it returns.

The scaffolds come from hand-written templates in `tooling/helix-kmp/templates/`, derived from the
`:feature:posts` / `:capability:posts-*` reference slice. They compile and pass `detektAll`,
`spotlessCheck` and `checkModuleGraph` as written -- no reformatting pass required. If you change a
template, run `tooling/helix-kmp/tests/run-tests.sh`.

### Persisting data from a Capability

Apply SQLDelight in the Capability implementation, declare `AppDatabase` in its `<package>.db`,
and keep its `.sq`/`.sqm` files under the matching `src/commonMain/sqldelight/<package>/db/`
directory. Build its generated database with `LazyDatabase` over the app's shared
`SqlDriverProvider`. Register the implementation by hand as both a SQLDelight contributor and a
normal dependency of `:storage:database`. Use one physical database by default (hard budget: five),
keep migration numbers unique repo-wide, and put cross-capability joins/projections in
storage-owned `.sq` files when they are genuinely assembly-level.

The `.sq` schema must describe the exact migrated shape, including column order and defaults. As
part of `check`, the storage assembly verifies the merged sequence from the checked-in snapshots in
`storage/database/src/commonMain/sqldelight/databases/`; the capability-impl role disables isolated
contributor verification because repo-wide numbering is intentionally non-contiguous per module.

## Control plane

Stage **P1** is implemented through the stable Bash 3.2 wrapper. The graph-backed commands use
Python 3's standard library and refresh `build/reports/helix/module-graph.json` unless
`--no-refresh` is supplied.

| Command | Purpose |
| --- | --- |
| `helix-kmp create ...` | Scaffold a Feature, Capability, or Cell |
| `helix-kmp verify ...` | Run fast/full gates or the agent-instruction no-drift check |
| `helix-kmp graph [<module>]` | Show schema-2 nodes, edges, reverse edges, and cycles |
| `helix-kmp impact <target>` | Resolve module/file/type blast radius, tests, owners, and consumers |
| `helix-kmp doctor [<scope>]` | Explain findings and cheap architecture-pressure evidence |
| `helix-kmp context <target>` | Emit the bounded eight-section task packet |
| `helix-kmp gallery` | Index Feature Screens, Cells, and fixture states; no launcher or rendering |

The schema-2 report stores `nodes[{path,role,roles,projectDir,targets,publicApiDirs}]`,
`edges[{from,to,configuration}]`, and `findings[]`. `graph --json` adds `reverseEdges` without
changing the Gradle-owned report. Run `tooling/helix-kmp/helix-kmp help` for all flags.

## Running it

```bash
./gradlew :app:android:installDebug                     # Android, on a connected device or emulator
./gradlew :app:desktop:run                              # Desktop (JVM)
./gradlew :app:web:wasmJsBrowserDevelopmentRun          # Web, dev server with hot reload
./gradlew :app:desktop:packageDistributionForCurrentOS  # Desktop installer (dmg / msi / deb)
```

iOS builds from Xcode against the static framework produced by `:app:shared`; open `app/ios/` and run (the target links `-lsqlite3` for the SQLDelight native driver).
Nothing in the Gradle loop above builds iOS except `./gradlew verifyFull`, which links the
simulator framework.

## Architecture documentation

| Document | What it answers |
| --- | --- |
| [`AGENTS.md`](AGENTS.md) | The working rules, in the form an agent needs them |
| [`docs/architecture/helix-kmp-source-of-truth.md`](docs/architecture/helix-kmp-source-of-truth.md) | Helix KMP in full -- the master source |
| [`docs/architecture/helix-adoption-plan.md`](docs/architecture/helix-adoption-plan.md) | How this repository adopted it, phase by phase, with status |
| [`docs/architecture/adr/0001-helix-adoption.md`](docs/architecture/adr/0001-helix-adoption.md) | Why Helix, what was rejected, and what it costs |
| [`docs/architecture/exceptions.md`](docs/architecture/exceptions.md) | How to add, expire and remove a rule exception |
| [`docs/architecture/network.md`](docs/architecture/network.md) | How the shared HTTP client, authentication and retries are assembled |
| [`docs/architecture/preferences.md`](docs/architecture/preferences.md) | How preferences, documents and secrets are stored on every target |
| [`build-logic/README.md`](build-logic/README.md) | The convention plugins, the DSL and the enforcement tasks |
| [`config/helix/dependency-policy.json`](config/helix/dependency-policy.json) | The dependency matrix itself -- the single source |

## Status

The control plane is at stage **P1**. Only the evidence-earned P2 extraction and migration
codemods remain unbuilt; Resource Inspector hooks and a Live Resource reference slice are also
future work rather than part of this thin command layer.
