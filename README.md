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
| `:capability:identity-impl` | capability_impl | Credential persistence and session ownership; supplies `BearerTokenSource` |
| `:capability:posts-api` | capability_api | `PostsQueries`, `PostsCommands`, and the `Post` / `PostFeed` product models |
| `:capability:posts-impl` | capability_impl | The posts implementation -- Store5 over network and database, `internal` but for its Koin module |
| `:feature:posts` | feature | The posts Screens, Cells, ViewModels and Outputs |
| `:foundation:network` | foundation_runtime | Ktor client setup, `ApiError`, result mapping |
| `:foundation:preferences` | foundation_runtime | DataStore-backed key-value preference stores |
| `:foundation:presentation` | foundation_api | `FeatureInstanceKey`, `CellPlacementId`, `CellSpec` |
| `:foundation:resource` | foundation_api | `ResourceObservation`, freshness, refresh QoS, problem taxonomy |
| `:foundation:runtime` | foundation_runtime | Application scope, dispatchers, logging |
| `:platform:connectivity` | platform | Network-reachability seam, per platform |
| `:storage:database` | storage | SQLDelight database and its per-platform drivers |
| `:testkit:common` | testkit | `runMainTest`, fakes and fixtures -- test code only |
| `:ui:design-system` | ui | Theme and stateless rendering; no ViewModel, no Koin, no navigation |

Plus, outside the runtime graph:

| Path | What it is |
| --- | --- |
| `build-logic/` | The `bkp.*` convention plugins and the architecture rules -- see [`build-logic/README.md`](build-logic/README.md) |
| `tooling/helix-kmp/` | The Helix control plane (stage P0): the CLI, its templates and its tests |
| `config/helix/` | The dependency policy and the exception registry |
| `docs/architecture/` | The master source, the adoption plan, the ADRs |

## Verifying

```bash
tooling/helix-kmp/helix-kmp verify --fast --affected   # only the modules you touched
./gradlew verifyFast                                   # every module: JVM tests, detekt, spotless, module graph
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
adds it to `settings.gradle.kts`, and -- for a Cell -- registers its ViewModel in the Feature's
Koin module. What it does not do is load the new `<name>FeatureModule` or `<name>CapabilityModule`
into the composition root in `:app:shared`; do that yourself.

The scaffolds come from hand-written templates in `tooling/helix-kmp/templates/`, derived from the
`:feature:posts` / `:capability:posts-*` reference slice. They compile and pass `detektAll`,
`spotlessCheck` and `checkModuleGraph` as written -- no reformatting pass required. If you change a
template, run `tooling/helix-kmp/tests/run-tests.sh`.

Then run `tooling/helix-kmp/helix-kmp help` for the full surface, including which commands
deliberately do not exist yet.

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
| [`build-logic/README.md`](build-logic/README.md) | The convention plugins, the DSL and the enforcement tasks |
| [`config/helix/dependency-policy.json`](config/helix/dependency-policy.json) | The dependency matrix itself -- the single source |

## Status

The control plane is at stage **P0**: `create` and `verify` exist. `graph`, `impact`, `doctor`,
`context`, `gallery`, `extract` and `migrate` are described by the master source and are **not
built** here -- no stub, no partial output. `AGENTS.md` lists them explicitly so that neither a
person nor an agent assumes otherwise.
