# Helix adoption evidence report

Evidence contract: master source §22.13. Completion checklist: §29.7. Plan and per-phase
decisions: [helix-adoption-plan.md](helix-adoption-plan.md) §5–§6. Date: 2026-09-02.
The adoption was committed on 2026-09-02 as seven commits on `main`; those commits remain
unpushed. This report now also records the subsequent P1 control-plane working-tree change.

## Command results (2026-09-06)

- `foundation/resource/.../Outcome.kt` owns the library-free `Outcome<T>`, `Problem`,
  `ProblemKind` and `Violation<F>` contracts; the `when` expressions in the bridge and in
  `ui/design-system` are exhaustive without `else`.
- `capability/posts-api/.../PostsCapability.kt` and
  `capability/todos-api/.../TodosCapability.kt` demonstrate that every command returns one
  `Outcome<T>`, including `Outcome<Unit>` for commands with no domain result.
- `TodosCapability.kt` demonstrates refusals inside command-specific completed result types:
  create has `Created | InvalidInput`, update has `Updated | NotFound | InvalidInput`, and delete
  has `Deleted | NotFound`. None has an infrastructure-failure case.
- `TodosRemoteSource.kt` maps endpoint 404 and 422 answers into implementation-only answer values
  before the bridge. `PostsRemoteSource.kt` maps a detail 404 the same way. Unmapped statuses stay
  `NetworkFailure` values.
- `foundation/resource-runtime/.../CommandBridge.kt` is the sole generic classification and
  logging bridge. A Capability builds one `CommandBridge(logger, "<tag>")` and calls
  `bridge.commit(...)` / `bridge.toOutcome(...)`, so no call site carries a logger and every
  logged operation is prefixed with that tag. `CommandBridgeTest` pins the mapping, the severity
  rule and the structured fields the master document §13.7 requires.
- `SyncCoordinator` mints two failures of its own. A sync worker that threw is a defect and is
  reported through `CommandBridge.unexpected` at error severity; a worker abandoned because its
  scope ended has nothing to diagnose and is not logged. `SyncCoordinatorTest` pins one entry for
  the former, none for the latter, and none for an outcome the sync function
  itself produced, which the Capability's own bridge already logged.
- Logging is configured once, at the App root (master source §18.8). `app/shared/.../di/KoinApp.kt`
  builds the single Kermit `Logger` from the entry point's own debug signal, at `Severity.Verbose`
  in a debug build and `Severity.Warn` otherwise, and hands the same logger to Koin through
  `kermit-koin`, so Koin's diagnostics land where everything else does on every target rather than
  in Logcat alone. iOS keeps Kermit's Xcode-tuned default writer in debug and writes through `OSLogWriter` with the application id as its subsystem in release. Every
  module that receives the logger tags it with its own name where it takes it in: `CommandBridge`,
  `KermitKtorLogger`, the runtime exception handler, `:foundation:preferences` and
  `:platform:secure-storage`. `PreferenceCorruptionTest` and `KeysetVaultFallbackTest` pin the two
  fallbacks that used to be silent, each warning once and neither carrying file contents or key
  material. Feature modules still log nothing.
- `foundation/network/.../SafeCall.kt` and `Client.kt` keep kotlin-result and transport details
  inside implementations while attaching the final attempt's `X-Request-Id` to every failure
  kind. `SafeCallTest` covers HTTP, transport, decoding and unexpected request ids plus
  cancellation.
- `ui/design-system/.../Failure.kt` owns generic problem strings and maps every `ProblemKind`.
  Server display text remains on `Violation.message`; feature ViewModels map only stable kinds.
- `ResourceObservation.kt` defines confirmed absence as a null value with `Idle` and "no sync has
  completed in this process" as `ResourceOperation.Unsynchronized`, which `SyncStatus.toOperation()`
  reports until an attempt succeeds. `isInitialLoading` is the single derived question the
  ViewModels ask instead of each re-deriving it. Posts and Todos item syncs delete their durable
  rows on 404; `TodosCapabilityImplTest`, `PostViewModelTest` and `TodosViewModelTest` prove
  persistence and presentation behavior.

## Sync coordinator replaces the owned Snapshot runtime (2026-09-05)

On 2026-09-05, phase 1 of Capability boilerplate reduction extracted the reusable asynchronous
SQLDelight observation shapes into `:foundation:sqldelight` and added `commit` to
`:foundation:resource-runtime`; `:capability:posts-impl` adopted both. The current graph therefore
increases by one node, from 20 to 21, without changing value ownership or adding an exception.
Phase 2 centralised the remaining wiring without changing that graph:

- `SqlDriverProvider` and `LazyDatabase` replaced per-Capability database-provider interfaces and
  app bridges; `:storage:database` now supplies the single open, migrated driver.
- `ConnectivityMonitor.shared(applicationScope)` moved cold-flow sharing to the app root, so each
  coordinator passes `reconnects()` directly while all Capabilities share one platform callback.
- `:testkit:common` now owns the in-memory SQLDelight driver and provider fixtures used by
  implementation tests.
- `helix-kmp create` registers generated Capability and Feature modules in the app composition
  root, while its end-to-end test verifies the resulting real Koin graph. SQLDelight storage-schema
  contributor wiring remains an explicit post-create step because the default scaffold is
  in-memory.

A shared sync-marker table and a coordinator factory were considered and skipped. Six lines of
marker SQL did not justify a cross-module SQLDelight schema dependency and its build coupling;
sharing connectivity once at the root removed the only repeated coordinator construction concern
that would have justified a factory. Koin's `binds` form and a recording Ktor mock-engine fixture
were tried and reverted: each saved a few lines and cost readability.

The owned `SnapshotResource` ledger (below) was reviewed against the Store5 baseline at 0240602 and
against a minimal coordinator design. Both value-owning runtimes duplicated what SQLDelight already
holds and forced a second consistency argument (confirmed value versus durable row) onto every
Capability. The replacement keeps `:foundation:resource-runtime` but changes what it owns:

- `SyncCoordinator<Key>` schedules at most one sync per key, joins concurrent callers, isolates
  caller cancellation from the shared worker, applies `minInterval` for `syncIfDue`, counts
  observers per key through `observing(key, upstream)`, restarts only observed keys whose last
  attempt failed offline when its `retryTriggers` flow emits (Capabilities pass
  `ConnectivityMonitor.reconnects()`), evicts idle entries beyond `maxEntries`, and exposes
  `status(key)` as `Flow<SyncStatus>` (`inFlight`, `lastFailure`, `hasSucceeded`). It never sees a
  value. `observations(key, values)` wraps a durable value flow in `observing` and applies the
  contract's `SyncStatus.toOperation` mapping.
- `:capability:posts-impl` owns the value: `postFeedEntry` (migration `4.sqm`) records feed
  membership and order, while `postFeedState` carries the initialised marker row. An empty
  synchronised feed is `Idle` with an empty list while a never-synchronised feed is
  `Unsynchronized` with `null`.
  `replaceFeed` is one transaction (upsert rows, replace entries, set marker). Each Query hands its
  durable value flow (the feed combined with its marker, or the detail row) to
  `coordinator.observations(key, values)`.
- `:foundation:resource` lost `ResourceFreshness`; `ResourceObservation(value, operation)` is the
  whole contract. `:feature:posts` needed only the removal of the freshness read.
- Templates (`capability-impl`, `feature-capability`) ship an in-memory local source,
  `observations`, reconnect wiring and `close()` as generated code. They point to
  `:capability:posts-impl` as the SQLDelight and transactional durable example, and the feature
  fixtures follow the new state shape.
- Master doc: ADR-43 supersedes the 2026-09-04 note under ADR-05; §13.7, §14.2, §14.3, §14.5,
  §15.1 to §15.3, §18.4, §26 and §27.6 describe the coordinator contract.

Verification for this replacement:

| Command | Result |
|---|---|
| `./gradlew spotlessApply -q` | PASS, exit 0 |
| `./gradlew :foundation:resource:check :foundation:resource-runtime:check :capability:posts-impl:check :feature:posts:check -q` | PASS, exit 0; resource 6 tests x 4 targets, resource-runtime 22 tests x 4 targets, posts-impl 16 JVM tests, feature:posts 9 JVM + 3 x 3 other targets, 0 failures |
| `./gradlew :storage:database:check -q` | PASS, exit 0; `verifyMigrations` accepted `4.sqm`, 2 JVM + 1 wasm tests |
| `./gradlew :app:shared:jvmTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs :app:shared:compileTestKotlinIosSimulatorArm64 -q` | PASS, exit 0, 4 JVM tests |
| `./gradlew :app:android:assembleDemoDebug -q` | PASS, exit 0 |
| `./gradlew verifyFast -q` | PASS, exit 0, 0 failures across every module |
| `tooling/helix-kmp/tests/run-tests.sh` | see redesign report; rerun after template fixes |
| grep for `SnapshotResource`, `ResourceFreshness`, `revalidate` in Kotlin and Gradle Kotlin sources | PASS, no matches |
| added-line em dash check | PASS, no matches |

### Historical note: owned Snapshot runtime (2026-09-04) and its release-gate repairs (2026-09-05)

`:foundation:resource-store5` was first replaced by an owned `SnapshotResource<Key, Value>` that
kept a process-bounded per-key ledger (confirmed value, freshness inferred from read origin,
first-observer bootstrap, coalesced refresh, reconnect revalidation). Six release-gate repairs
followed on 2026-09-05 (durable re-read before labelling fresh, self-healing of a vanished confirmed
value, identity-guarded in-flight clearing, mutex-serialised ledger mutation with an invalidation
generation, retryable failure for an alive caller of a cancelled worker, and deterministic
interleaving tests). All of those verification chains passed (135 target test executions, 0
failures). The design was nevertheless retired the same day because the ledger duplicated the
database's value and every Capability had to reason about two owners; the sync coordinator above
is the replacement, and 0240602 remains the Store5 reference point.

## Storage assembly (2026-09-02)

The posts Capability implementation now owns its SQLDelight schema, migrations, local store, and
generated `…capability.posts.impl.db.AppDatabase` interface. `:storage:database` contributes a
comment-only assembly `.sq`, composes the posts schema into
`…storage.database.db.AppDatabase`, and owns the platform drivers, opening, the merged migration
sequence, and `app.db`. `AppDatabaseDriverProvider` supplies its open, migrated driver through the
shared `SqlDriverProvider`; each SQLDelight-backed Capability builds its generated database over
that driver. The proof-only Identity schema and cross-capability author join were removed, so the
composed schema is version 4; migration 3 renames the feed-state table without changing its data.

The assembly now enables SQLDelight migration verification against the checked-in version-1
composed snapshot; contributor-local verification is disabled because repo-wide numbering makes
its isolated gap check invalid. This wiring caught `PostsSchema.sq` placing `author_id` second without its
`DEFAULT 0`, while SQLite migration 2 appends that column last with the default; the fresh schema
now matches the migrated table exactly.

The dependency policy now allows Storage → Capability Impl and no longer allows Capability Impl →
Storage. `STORAGE-MIGRATION-DUPLICATE` rejects a migration version used in more than one module;
`STORAGE-MIGRATION-NAME` rejects non-positive/non-integer `.sqm` names. Both are covered by
`BkpHelixGraphTest`, and the migration source directories are configuration-cache-safe task inputs.

Verification completed for this migration:

| Command | Result |
|---|---|
| `./gradlew spotlessApply` | PASS, `BUILD SUCCESSFUL in 11s`, 80 tasks |
| `./gradlew -p build-logic spotlessApply` | PASS, `BUILD SUCCESSFUL in 1s`, 4 tasks |
| `./gradlew -p build-logic :convention:test` | PASS, `BUILD SUCCESSFUL in 29s`, 7 tasks |
| `./gradlew :storage:database:verifyCommonMainAppDatabaseMigration :capability:posts-impl:verifyCommonMainAppDatabaseMigration --console=plain` | PASS in 4s; storage executed, contributor `SKIPPED` |
| storage verifier with the known `PostsSchema.sq` drift temporarily restored | Expected FAIL in 2s; reported `author_id` default removal and column ordinal changes |
| `./gradlew :storage:database:jvmTest :capability:posts-impl:jvmTest --rerun-tasks` | PASS, `BUILD SUCCESSFUL in 6s`, 31 tasks |
| `./gradlew checkModuleGraph checkHelixPolicySync` | PASS, `BUILD SUCCESSFUL in 964ms`, no findings |
| `./gradlew verifyFast --continue` | PASS, `BUILD SUCCESSFUL in 30s`, 1,042 tasks |
| wasm development webpack + Android debug assemble | PASS, `BUILD SUCCESSFUL in 31s`; worker JS and `sql-wasm.wasm` emitted |
| iOS simulator framework link | PASS, `BUILD SUCCESSFUL in 16s` |
| `./gradlew :app:android:assembleDebug` | PASS, `BUILD SUCCESSFUL in 45s`, 327 tasks |
| `tooling/helix-kmp/tests/run-tests.sh` | PASS; scaffolded modules verified and working tree restored |
| `tooling/helix-kmp/helix-kmp verify --agents` | PASS, synchronized |

SQLDelight issue #5320 reproduced: with no storage-owned `.sq`, the generation task succeeded but
emitted no storage `AppDatabase`. The comment-only assembly file is therefore required. The file
is `app.db` on every platform; the earlier `posts.db` cache is simply left behind and refetched,
and the JVM `migrateEmptySchema` special case for pre-release desktop files was dropped with it.
The now-unused JVM in-memory assembly helpers were removed.

## Target/owner changed

| Before | After |
|---|---|
| `:shared:app`, `:androidApp`, `:desktopApp`, `:webApp` | `:app:shared`, `:app:android`, `:app:desktop`, `:app:web` |
| `:shared:features:list`, `:shared:features:details` (FeatureBundle, `UiState`/`FlowUseCase`) | `:feature:posts` (public entries only under `…feature.posts.api`) |
| `:shared:libs:posts` (historical repository + Store5 implementation) | `:capability:posts-api` + `:capability:posts-impl` |
| `:shared:libs:arch:core` | removed (patterns replaced by `:foundation:resource` / `:foundation:presentation`) |
| `:shared:libs:coroutines-x` | `:foundation:runtime` (+ `ApplicationRuntimeScope`) |
| `:shared:libs:networking` | `:foundation:network` |
| `:shared:libs:designsystem` | `:ui:design-system` |
| `:shared:libs:connectivity` | `:platform:connectivity` |
| `:shared:libs:database` | `:storage:database` |
| `:shared:libs:prefs` | `:foundation:preferences` (mechanism) + `:capability:identity-api` / `:capability:identity-impl` (credentials/session) |
| — | `:foundation:resource`, `:foundation:presentation`, `:testkit:common` (new) |

Owner of every module is the repository (single-owner template); no CODEOWNERS change.

## Architecture classification used

Helix KMP Baseline v1 / Master 1.3 one-time adoption (§29). Roles applied per module via
`bkp.kmp.<role>` convention plugins; dependency policy is the verbatim §9.0 JSON in
`config/helix/dependency-policy.json` (schema 2) with an empty `config/helix/exceptions.json`.
The `:feature:posts` slice is classified as a Snapshot Resource Feature over a grouped
Capability (`PostsQueries` / `PostsCommands`); there is no Live Resource in this template.

## Files/modules changed

- **build-logic**: role plugins (`BkpKmpRolePlugins.kt`), `HelixRole`, `HelixGraphTasks`
  (`checkModuleGraph`, `checkHelixPolicySync`), `verifyFast` / `verifyFull`; legacy
  `bkp.kmp.lib.compose` and `bkp.kmp.feature.compose` plugins deleted.
- **18 modules** with build scripts (see `settings.gradle.kts`): 4 app, 1 feature,
  4 capability, 5 foundation, 1 platform, 1 storage, 1 testkit, 1 ui.
- **Control plane**: `tooling/helix-kmp/` (CLI, templates, tests), `AGENTS.md`, `CLAUDE.md`,
  `.agents/skills/*`, `docs/architecture/adr/0001-helix-adoption.md`,
  `docs/architecture/exceptions.md`, `README.md`.
- **iOS**: Xcode project moved to `app/ios/`; `app/ios/iosApp.xcodeproj/project.pbxproj` Gradle task and framework search path
  now point at `:app:shared`.
- Full per-phase file lists and decisions: plan §6.

## Verification command + result

All runs on 2026-09-02, macOS, JDK per `gradle/libs.versions.toml`, configuration cache on.

| Command | Result |
|---|---|
| `./gradlew -p build-logic :convention:test` | BUILD SUCCESSFUL (Helix graph 11 cases, including schema-2 metadata, plus role plugins and validation) |
| `./gradlew help --configuration-cache` ×2 | entry stored, then reused |
| `./gradlew checkModuleGraph checkHelixPolicySync` | BUILD SUCCESSFUL, 0 findings, 0 exceptions, 18 modules; report `build/reports/helix/module-graph.json` |
| `./gradlew spotlessCheck detektAll` | BUILD SUCCESSFUL |
| `./gradlew check --continue` | BUILD SUCCESSFUL (final run after the review fixes; 0 failures in every test-result XML under the current modules). First run had 3 wasmJs-only failures in `PostViewModelTest` (fixed in `runMainTest`, see Fixture/golden evidence). Targets: jvm, Android host, iosSimulatorArm64, wasmJs browser |
| `./gradlew :app:android:assembleDebug :app:desktop:compileKotlin :app:web:wasmJsBrowserDevelopmentWebpack` | BUILD SUCCESSFUL |
| `./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL (`SharedApp.framework`) |
| `xcodebuild -project app/ios/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' ARCHS=arm64 build` | BUILD SUCCEEDED after adding `-lsqlite3` to `OTHER_LDFLAGS` (first run failed on undefined `sqlite3_*` symbols; see Exceptions/risks) |
| iOS Simulator smoke test (iPhone 17, iOS 26.2): fresh install of the `xcodebuild` output, feed → detail → back | PASS after the `StoreResource` fix below. The first run on a fresh database logged `Uncaught application-runtime failure` (`IllegalArgumentException: An observation without a value must be refreshing or failed`) and left the feed and detail screens on a spinner; see Review findings. |
| `tooling/helix-kmp/tests/run-tests.sh` | PASS (scaffold, verify, remove, working tree byte-identical) |
| `tooling/helix-kmp/helix-kmp verify --fast` | BUILD SUCCESSFUL |
| `./gradlew verifyFast --continue` (P1 final loop) | BUILD SUCCESSFUL; schema-2 graph has 18 nodes and zero findings |
| `tooling/helix-kmp/helix-kmp verify --agents` | PASS; generated sections and Skill command mentions synchronized |
| P1 command smoke matrix with `--no-refresh` | PASS: graph JSON, three impact target forms, clean/negative doctor, eight-section context, gallery index |
| `shellcheck` on the CLI | not run: shellcheck is not installed on this machine |

`./gradlew build` was deliberately not run (known OOM without capped workers; the routine
verification tier is `check` plus debug assembles).

## Tests added/updated

- `build-logic`: `BkpHelixGraphTest` (rule IDs, cycles, path/role mismatch, public surface
  outside `.api`, config-cache regression), `BkpHelixRolePluginTest`.
- `:foundation:resource` `ResourceObservationTest`; `:foundation:runtime`
  `ApplicationRuntimeScopeTest`; `:foundation:presentation`
  `StatefulLazyItemRegressionTest` (keyed Cell owner regression, §12.6, 9 cases) and
  `ViewportKeysRegressionTest` (viewport-window retention/retirement, 3 cases; 12 total).
- The keyed owner is now built on lifecycle 2.11.0 `ViewModelStoreProvider`; the repository-pinned
  first-party comparison retained the narrow Helix host contract while deleting its custom store
  registry. The host constructs the provider directly instead of using
  `rememberViewModelStoreProvider`, which clears every key when its call site leaves composition
  (a hidden host must keep its Cells; `hostLeavingCompositionWhileParentStoreLivesRetainsChildStores`).
- `:capability:posts-impl` `PostsCapabilityImplTest` (first-observer sync, reconnect retry of
  observed offline failures only, coalesced capability-scope work, transactional feed replacement, marker-based
  empty versus never-synchronised feeds, offline cold start).
- `:feature:posts` `PostViewModelTest` (3 common cases), `PostContentTest` (3 JVM cases), and
  `PostDetailCellTest` (3 JVM cases, including lazy-list composition and instance-key/post-id drift
  rejection); `PostContentPreviews.kt` provides 6 stateless detail/feed state previews.
- `:app:shared` `KoinGraphTest` (DI graph verifies), `RootContentTest`.
- Moved with their modules: network `ClientTest`/`SafeCallTest`, storage
  `PostsLocalSourceTest`, `DataStorePreferenceStoreTest` in `:foundation:preferences`, and
  `CredentialStoreTest` / `IdentityCapabilityImplTest` in `:capability:identity-impl`.
- Deleted with their modules: list/details ViewModel, repository and content tests,
  `UiStateTest`.
- Tooling: `tooling/helix-kmp/tests/run-tests.sh`; schema-2/P1 fast command checks, agent drift
  positive/negative checks, and denied-edge doctor diagnosis now run alongside the scaffold tests.

## Fixture/golden evidence

`:testkit:common` provides `FakePostsQueries`, `FakePostsCommands`, `PostsFixtures`,
`ResourceObservationFixtures`, `testDispatchers`, `runMainTest`. No golden/screenshot files
exist in this template (none existed before either).

`runMainTest` installs the test `Main` dispatcher *inside* the `runTest` body and drains the
scheduler before `resetMain()`. On JS/Wasm `runTest` returns a Promise before the body runs,
so a `try`/`finally` around `runTest` resets `Main` first and `viewModelScope` silently falls
back to the non-dispatching `Dispatchers.Main.immediate`; that made the three
`PostViewModelTest` cases fail only on `wasmJsBrowserTest` during phase 6. Draining before the
reset is what keeps `WhileSubscribed` teardown off an absent JVM `Main` dispatcher.

## Impact report

Everything downstream of `:shared:*` was affected: all four app shells, the iOS Xcode build
phase, the Koin composition root (`app/shared/.../di/KoinApp.kt`), the Nav3 route set. No
external consumer exists. The `MIGRATION_REPORT.md` from 2026-08-14 is kept as history with
a header stating its paths are stale. The P1 `helix-kmp impact` command now automates the
module/file/type reverse-dependency slice; this narrative remains the adoption-time impact record.

## Public API/ABI changes

- **New public surfaces**: `ResourceObservation`, `ResourceOperation`, `Outcome`, `Problem`,
  `ProblemKind`, `Violation`, `RefreshQos`, `RefreshPriority`, `NetworkPreference`
  (`:foundation:resource`); `ApplicationRuntimeScope`
  (`:foundation:runtime`); `FeatureInstanceKey`, `CellPlacementId`, `CellSpec`,
  `StatefulLazyItem`, `KeyedOwnerHost`, `rememberViewportKeys`
  (`:foundation:presentation`); `PostId`, `Post`, `PostFeed`, `PostsQueries`, `PostsCommands`
  (`:capability:posts-api`); `postsCapabilityModule` (`:capability:posts-impl`);
  `PostFeedScreen`, `PostDetailScreen`, `PostDetailCell`, `PostFeedOutput`,
  `PostDetailOutput`, `postsFeatureModule` (`:feature:posts`).
- **Removed**: FeatureBundle, `UiState`, `FlowUseCase`, `AppUseCaseFailureListener`, the
  list/details feature APIs, and `ApiResultX` (Result mapping now lives in
  `:capability:posts-impl`).
- **Apple framework**: `baseName` stays `SharedApp` so Swift imports are unchanged; the
  binary `bundleId` follows the new package.
- **Android**: `namespace` / `applicationId` `dev.mayankmkh.basekmpproject.androidapp` kept.
- No binary-compatibility validator is configured (none was before).

## Qualification suites touched

`verifyFast` = `checkModuleGraph`, `checkHelixPolicySync`, and every module's `spotlessCheck`,
`detektAll`, metadata compile and `jvmTest`/`test`; `verifyFull` = the fast tier plus
`:app:android:assembleDebug`, the `:app:web` development/production webpack bundles and
`:app:shared:linkDebugFrameworkIosSimulatorArm64` (root `build.gradle.kts`). Neither tier runs
the included build-logic TestKit tests or the tooling self-test; both are run separately
(the self-test edits `settings.gradle.kts` and runs Gradle recursively). Target qualification: Android debug APK, desktop compile, wasmJs dev bundle,
iOS simulator framework link.

## Exceptions/risks

- `config/helix/exceptions.json` is empty. The single policy deviation lives in code, not in
  an exception: the validator allows `app → app` edges so the target shells can depend on
  `:app:shared` (plan §6 phase 4 and ADR 0001 explain why).
- **§29.7 P1 control-plane gate is met**: thin `graph`, `impact`, `doctor`, `context`, and
  gallery-index commands are implemented, and `verify --agents` regenerates/checks marked
  `AGENTS.md` sections plus Skill command availability. Resource Inspector hooks remain unmet.
- No Live Resource exists in the slice, so "one Live Resource shares correctly" is satisfied
  only by the `stateIn`/`WhileSubscribed` sharing in the Feature, not by a Capability-level
  live stream.
- `RefreshQos` is recorded at the command boundary but every priority executes immediately;
  there is no refresh scheduler.
- The seven adoption commits are local to `main` and remain unpushed; this P1 work is intentionally
  left as working-tree changes for review.
- The iOS link against the static `SharedApp` framework had been broken since the SQLDelight
  native driver landed on 2026-09-01 (undefined `sqlite3_*` symbols); the Xcode project was last
  touched on 2026-08-15 and never gained `-lsqlite3`. Found while verifying the `app/ios/` move
  and fixed in `project.pbxproj` for both configurations.
- Codex CLI reached its usage limit during phase 3; phases 3 (wrap-up) to 6 were done by
  Claude agents, as the user's delegation rule requires.

## Review findings (phase 6)

A read-only review of the whole diff (Fable 5.1) returned 18 findings and a list of confirmed
properties (policy JSON verbatim, Feature public surface only under `.api`, Cell signature,
no `Dispatchers.*`/`GlobalScope` in Feature or Capability main sources, no Koin outside app and
Capability impl, sync status to operation mapping, keyed-owner regression, DI graph test, every command
named in `AGENTS.md`/`README.md`/Skills exists, no live `:shared:` references).

Fixed in phase 6 (details in the plan §6 phase 6 row):
- `helix-kmp create capability identity` got the roles, packages and settings registration right;
  its generic `IdentityId`/`IdentityRecord`/`refresh` in-memory shape was wrong for Identity and was
  replaced with the session API, credential persistence and network inversion. App Koin
  registration remained manual by design.
- `:app:web` emitted `web.js` after the module rename while `index.html` loads `webApp.js`
  (page loaded nothing). `BkpWebAppPlugin` now pins `outputFileName = "webApp.js"`.
- `StoreResource.refresh` ran the network fetch in the caller's coroutine, so clearing a
  ViewModel mid-fetch left every observer at `Refreshing` forever. The fetch now runs in the
  resource child scope through `async { }.await()`, and a `finally` settles an abandoned refresh;
  regression test added to `PostsCapabilityImplTest`.
- `RootContent` popped an unguarded back stack; a web session restored straight onto a detail
  route would empty the stack on Back. Pop is guarded and falls back to the feed route;
  test added to `RootContentTest`.
- `helix-kmp` asked for the unregistered `:app:web:test` task in affected verification.
- Adoption plan §5 still said `ApiResultX.getOrThrow` was retained; it was removed in phase 3.
- `StoreResource` `NoNewData` branch now respects the `ResourceObservation` null-value
  invariant.
- `StoreResource` built its observation with `combine(valueFlow, syncFlow)`. `combine` collects
  each source in its own coroutine, so on a multi-threaded dispatcher (iOS `Dispatchers.Default`,
  reproduced on every fresh install in the Simulator) it could see the new `Idle` sync state before
  the value written just ahead of it, construct an `Idle` observation without a value, trip the
  `ResourceObservation` invariant and kill the eager collector: every observer of that key stayed
  on `Refreshing` for the process lifetime. The resource now holds one
  `MutableStateFlow<ResourceObservation>` and every transition is a single atomic `update`, so an
  invalid intermediate state cannot exist. Found by the Simulator smoke test, not by the JVM/wasmJs
  unit tests (single-threaded test dispatcher).

Historical resource design-review follow-up (2026-09-03, superseded 2026-09-04):

- Refresh commands gained an explicit attempt result; `StoreResource` moved to
  `:foundation:resource-store5`; transport classification became typed in `:foundation:network`;
  and display messages were removed from infrastructure problems.
- The Store5 adapter records QoS but executes every class immediately because no scheduler exists
  yet, and it always demotes a cached value to `STALE` on failure.
- The adapter gates source-of-truth collection on subscribers with a grace timeout. Posts detail
  resources are lease-counted and evicted after the last collector or refresh finishes, and a
  reconnect refreshes the feed only while it has an observer and otherwise defers the refresh to
  the next observer.

Network module review follow-up (2026-09-04):

- The credential contract is aligned to §18.6.1, with host-scoped bearer injection and no-auth
  requests never refreshed or retried with a token known to be invalid.
- `NetworkFailure.Http` is status-first and leaves Capability-owned error body decoding optional.
- Every request carries an `X-Request-Id`, `NetworkConfig` owns all three client timeouts, app code
  owns its environment base URL after `BaseUrls` was deleted, and `:foundation:network` now enables
  explicit API mode.

Network ground-up redesign (2026-09-04):

- One client now serves public and protected endpoints through per-request `authenticated()` and
  `retryable()` opt-ins.
- Bearer auth uses the Identity-owned in-memory snapshot with `cacheTokens = false`, so sign-out
  and account switches cannot leave a second token cache behind.
- Android and desktop name OkHttp, iOS names Darwin, and wasmJs names Js directly rather than
  resolving an engine from the classpath.
- `NetworkFailure` is a value model with copied status, headers and body bytes; Capability
  implementations decide whether and how to decode product error bodies.
- The app and client share strict JSON semantics: unknown keys are ignored, lenient parsing and
  explicit nulls/defaults are disabled, and input values are coerced.

Preferences and secure-storage redesign (2026-09-04):

- `:foundation:preferences` now owns typed DataStore preferences, lenient JSON document stores,
  corruption recovery, stable platform paths and early process-wide file collision detection.
- The new cohesive `:platform:secure-storage` module uses Android Keystore-wrapped Tink, iOS
  Keychain items, a desktop DataStore encrypted with Tink under an OS-held keyset and process-memory storage on web.
- Identity moved its `token` from plain preferences to `SecretStore`; App supplies the stable
  application identifier and both platform-shaped storage contexts through Koin.
- Common store contracts, JVM corruption recovery, serializer round trips, Tink encryption and
  ciphertext tamper recovery have dedicated regression tests.

Recorded, not fixed (follow-ups, none blocks Feature development):

- Validator hard-codes the `app → app` allowance instead of reading it from policy data
  (deliberate, ADR 0001; the policy JSON must stay verbatim).
- `readExceptions` fails with a raw exception on a malformed `exceptions.json` entry instead
  of an `EXC-MALFORMED` finding.
- Graph validation only follows `api`/`implementation`-style configurations, so a forbidden
  edge declared via `compileOnly`/`runtimeOnly` is not reported.
- `currentDate` is an `@Input` of the cacheable graph task, so it is never up-to-date across
  days.
- `KeyedOwnerHost` reconciles a set diff against remembered known keys in `SideEffect`; lifecycle's
  provider defers a requested clear while an item remains composed and completes it on disposal.
- `StoreResource` serialises overlapping refreshes behind a mutex rather than sharing the
  in-flight fetch.
- `PUBLIC_TOP_LEVEL` matching in the graph task is line-based; a one-line
  `@Composable public fun` would not be detected (ktfmt keeps annotations on their own line).
- `helix-kmp` iterates changed files with unquoted word splitting (paths with spaces).
- Role→path, rule-ID and verify-tier tables are repeated across `AGENTS.md`, `README.md`,
  `build-logic/README.md` and the adoption plan; `build-logic/README.md` also describes the
  root-task walk inaccurately. "One source per fact" is not yet met for these tables.
- Unused version-catalog aliases (`koin-compose`, `kotlinx-coroutines-android`, `truth`,
  `androidx-lifecycle-runtimeTesting`, possibly the `compose-*` entries).

## Todos slice

- Capability API product language and command-specific results:
  `capability/todos-api/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/capability/todos/api/`.
- SQLDelight ownership, list/detail sync coordinators, optimistic reconciliation, reconnect retry,
  and the documented fake-backend merge rule: `capability/todos-impl/src/commonMain/`.
- Capability-owned product preferences applied by SQL queries:
  `capability/todos-impl/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/capability/todos/impl/TodosSettingsSource.kt`
  and `TodosLocalSource.kt`.
- Profile C list, detail, and editor Screens plus the Profile B independently keyed summary Cell:
  `feature/todos/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/todos/`.
- Typed multi-screen navigation and adaptive compact/expanded destination chrome:
  `app/shared/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/app/shared/`.
- A second schema contributor and merged migration evidence:
  `storage/database/build.gradle.kts` and
  `storage/database/src/commonMain/sqldelight/databases/6.db`.

## Navigation shell

- The Navigation 3 Common UI and Multiple Back Stacks recipes are represented by the saveable
  Posts and Todos stacks, exit-through-home behavior, and flattened decorated entries in
  `app/shared/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/app/shared/nav/AppNavigationState.kt`
  and `ui/RootContent.kt`.
- The Basic Saveable and Compose Multiplatform polymorphic-serialization guidance is represented by
  the `SavedStateConfiguration` route registry in `app/shared/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/app/shared/App.kt`.
- The Material List-Detail recipe is represented by `ListDetailSceneStrategy` metadata in
  `ui/PostsEntries.kt` and `ui/TodosEntries.kt`, composed with the strategy in `ui/RootContent.kt`.
- Material adaptive destination chrome is represented by `NavigationSuiteScaffold` in
  `ui/RootContent.kt`; one `NavDisplay` remains at one composition position across layout changes.
- The Modular Koin recipe stops at app-owned product-area entry builders in `ui/PostsEntries.kt`
  and `ui/TodosEntries.kt`. Helix §12.2 and §12.3 keep route mapping in the app, and dependency
  policy forbids Feature modules from depending on app or Navigation 3 types.
- The Passing Arguments recipe is represented by typed route values being converted to domain IDs
  and stable `FeatureInstanceKey` values by the app-side entry builders before Features obtain
  parameterized Koin ViewModels.
- The Results Event recipe's shared event bus is deliberately not used. Helix §12.2 requires
  Features to emit semantic Outputs, which the app shell maps to route changes.
- The URI Arguments and Synthetic Back Stack recipes are represented by the stable fragment mapping
  in `nav/AppRouteBrowserHistory.kt` and the browser bridge in `nav/AppNavigationState.kt`; restored
  detail routes gain their list parent before rendering and popping.
