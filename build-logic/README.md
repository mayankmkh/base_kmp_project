### Original code at [NowInAndroid](https://github.com/android/nowinandroid/tree/a77d7b69b58238e2b5e934ab9e492c0932bf2904/build-logic)

# Convention Plugins

`build-logic` is an included build (configured in [`settings.gradle.kts`](../settings.gradle.kts))
that publishes the convention plugins used by all modules. It reads the same
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml) as the main build.

## Layout

```
convention/src/main/kotlin/dev/mayankmkh/basekmpproject/
├── convention/core/         catalog-backed config (BuildConfig) and shared dependency bundles
├── convention/dsl/          the bkpModule and bkpTargets extensions
├── convention/module/       the primary plugins
├── convention/quality/      Spotless + detekt (style), Android lint (lint)
└── convention/validation/   plugin-graph and DSL legality checks
```

The files directly under `dev/mayankmkh/basekmpproject/` are helpers shared by the plugins
(`KotlinMultiplatform.kt`, `SharedCompose.kt`, `BkpFlavor.kt`, …).

## Primary and role plugin model

Every adopted runtime module applies **exactly one** role plugin. KMP role plugins layer on the
internal `bkp.kmp.lib` base, which owns targets, Kotlin configuration, power-assert, the test
baseline, quality/lint, and local DSL validation. `bkp.kmp.lib` records no role of its own, so a
module that stops at the base — rather than applying a role plugin on top — fails `MOD-ROLE-MISSING`.

### Current module map

| Module | Role plugin | Purpose |
|---|---|---|
| `:foundation:runtime` | `bkp.kmp.foundation.runtime` | dispatchers and application runtime scope |
| `:foundation:network` | `bkp.kmp.foundation.runtime` | Ktor client construction and transport errors |
| `:foundation:preferences` | `bkp.kmp.foundation.runtime` | DataStore-backed key-value preference stores |
| `:foundation:resource` | `bkp.kmp.foundation.api` | resource observation and refresh QoS contracts |
| `:foundation:presentation` | `bkp.kmp.foundation.api` + Compose opt-in | keyed presentation identity/ownership |
| `:platform:connectivity` | `bkp.kmp.platform` | cohesive connectivity expect/actual mechanism |
| `:storage:database` | `bkp.kmp.storage` | SQLDelight schema, drivers, and database assembly |
| `:ui:design-system` | `bkp.kmp.ui` | public application theme |
| `:capability:identity-api` | `bkp.kmp.capability.api` | observable session state and sign-in/sign-out commands |
| `:capability:identity-impl` | `bkp.kmp.capability.impl` | credential persistence and the `BearerTokenSource` binding |
| `:capability:posts-api` | `bkp.kmp.capability.api` | grouped `PostsQueries` / intent `PostsCommands` and their models |
| `:capability:posts-impl` | `bkp.kmp.capability.impl` | Store5-backed posts resources; only `postsCapabilityModule` is public |
| `:feature:posts` | `bkp.kmp.feature` | feed/detail Screens and the reference Cell, public only under `…feature.posts.api` |
| `:testkit:common` | `bkp.kmp.testkit` | dispatcher helpers, posts fakes, and resource-observation fixtures |
| `:app:shared` | `bkp.kmp.app` | the composition root: Koin startup, Nav3 routes, `RootContent`, and the `SharedApp` Apple framework |
| `:app:android` | `bkp.android.app.compose` | Android shell and flavors; role `app` |
| `:app:desktop` | `bkp.desktop.app` | Compose Desktop shell; role `app` |
| `:app:web` | `bkp.web.app` | `wasmJs` browser shell, embeddable in a host page; role `app` |

Every module in the map carries a Helix role and is validated on its path. Nothing is grandfathered:
the adoption-era `:shared:*` tree is gone, and `:app` and `:capability` exist only as structural
parents without build scripts.

### Plugin selection guide

| Module is a… | Plugin | Notes |
|---|---|---|
| Android app | `bkp.android.app` | plain `debug`/`release` unless the module opts into flavors |
| Android app with Compose | `bkp.android.app.compose` | adds `bkp.android.app` |
| Android library (non-KMP) | `bkp.android.lib` | no consumers today |
| Android test module | `bkp.android.test` | |
| Base KMP library | `bkp.kmp.lib` | Internal base only; a module that applies it alone has no role |
| Helix KMP app root | `bkp.kmp.app` | Compose, lifecycle ViewModel, and Koin |
| Helix Feature | `bkp.kmp.feature` | Compose, lifecycle ViewModel, and Koin; no project dependencies |
| Helix UI | `bkp.kmp.ui` | Compose and strict explicit API |
| Capability API / Impl | `bkp.kmp.capability.api` / `.impl` | API is explicit; Impl receives Koin core |
| Foundation API / Runtime | `bkp.kmp.foundation.api` / `.runtime` | API is explicit and Compose is opt-in |
| Platform cohesive / API / Impl | `bkp.kmp.platform` / `.api` / `.impl` | API uses strict explicit API |
| Storage | `bkp.kmp.storage` | Physical persistence assembly |
| Testkit | `bkp.kmp.testkit` | Shared test primitives only |
| Desktop app | `bkp.desktop.app` | Kotlin/JVM, not KMP |
| Web app | `bkp.web.app` | KMP with a `wasmJs` browser target only |

Two plugins layer on top of a primary rather than replacing it:

- **`bkp.android.app.firebase`** — GMS, Crashlytics and Performance, plus the Firebase BOM.
- **`org.jetbrains.kotlin.plugin.serialization`** — applied directly where needed, deliberately not
  wrapped in a convention plugin.

Everything a primary plugin declares in `plugins {}` must also be declared `apply false` in the
[root build script](../build.gradle.kts), so the plugin is loaded once rather than per-subproject
classloader.

### What a primary plugin gives you

All of them apply `bkp.quality.style`, `bkp.quality.lint` and `bkp.validation.graph`. On top of
that:

- **Android SDK levels** come from the catalog (`android-compileSdk`, `android-minSdk`,
  `android-targetSdk`). No plugin hardcodes them.
- **`bkp.android.lib`** derives `namespace` from the Gradle project path, so library modules do not
  set it.
- **`bkp.android.app*`** owns the build-type convention: a `.debug` application id suffix, a minified
  release, the `META-INF/{AL2.0,LGPL2.1}` packaging excludes, `buildConfig = true`, and the
  instrumentation runner. An app module declares only what is genuinely its own — `namespace`,
  `applicationId`, version and signing. These are defaults, not decrees: a module's `android { }`
  block runs after the plugin and overrides any of them. The one exception is `proguardFiles`, which
  appends rather than assigns; a module's own `proguard-rules.pro` is picked up when the file exists,
  and no module is obliged to carry an empty one.
- **KMP primaries** declare no targets at all — the module picks them with
  [`kotlin { bkpTargets { … } }`](#target-selection). `bkp.web.app` is the exception: it layers the
  Compose plugins onto `bkp.kmp.lib` itself and then declares `web()`, because an app module named
  for the web has nothing to choose.
- **The test baseline** — `kotlin-test`, `kotlinx-coroutines-test`, Turbine and power-assert
  diagrams on the `kotlin.test` assertions — is the same for `bkp.kmp.lib` (`commonTest`) and
  `bkp.android.lib` (`test`), so a module is never the odd one out for the platforms it targets.
  On the Android side the compiler plugin is attached to the test compile tasks by hand: AGP 9's
  built-in Kotlin creates no `KotlinCompilation`, so the power-assert Gradle plugin would apply and
  do nothing.
- **Compose primaries** wire the Compose compiler, the shared Compose bundle, and the tooling
  renderer on the right configuration for the module type.
- **AGP 9 has built-in Kotlin**, so `org.jetbrains.kotlin.android` is never applied.

## `bkpModule` DSL

Primary plugins expose `bkpModule { ... }` for structured module configuration. A type-safe
accessor is generated, so no import is needed:

```kotlin
plugins {
    alias(libs.plugins.bkp.android.app.compose)
}

bkpModule {
    features {
        demoProdFlavors()
    }
}
```

Features are opt-in functions, not flags. Everything is off until a module asks for it, and calling
the function is the entire declaration — there is no value to pass and nothing to turn back off. A
line inside `features { }` therefore always means something, which a `set(true)` matching a
convention did not.

| Feature | Valid on |
|---|---|
| `demoProdFlavors()` | `bkp.android.app*` |
| `compose()` | `bkp.kmp.foundation.api` |

`demoProdFlavors()` registers `demo` and `prod` product flavors; without it the app stays on plain
`debug`/`release`. It is read in AGP's `finalizeDsl`, which runs after the module's `bkpModule`
block but still before variants are created — the last point at which flavors can be registered.
Reading it in `apply()` would always see it unset, and `afterEvaluate` is too late for DSL changes.
Anything that needs the flavors to exist while the build script is still being evaluated (a
`demoImplementation` dependency, a `productFlavors { }` block of its own) will not find them; no
module does that today.

### Firebase boundary

Firebase is a plugin, not a feature — applying `bkp.android.app.firebase` is the declaration, so
there is nothing to keep in sync with it. It is valid only alongside a `bkp.android.app*` primary.

## Target selection

KMP primaries create no targets. Every KMP module declares its own set, inside `kotlin { }`:

```kotlin
plugins {
    alias(libs.plugins.bkp.kmp.lib)
}

kotlin {
    bkpTargets { default() }
}
```

| Selector | Creates |
|---|---|
| `default()` | `android()`, `jvm()`, `ios()`, `web()` |
| `android()` / `android { }` | applies `com.android.kotlin.multiplatform.library` and configures its target |
| `jvm()` | the JVM target |
| `ios()` / `ios { }` | `iosArm64` and `iosSimulatorArm64` as a family |
| `web()` / `web { }` | `wasmJs` with a `browser()` compilation |

`android { }` and `ios { }` take the created target as their receiver, which is where per-module
target config goes:

```kotlin
kotlin {
    bkpTargets {
        android()
        jvm()
        ios {
            binaries.framework { baseName = "SharedApp" }
        }
    }
}
```

`android { }` exists because the module cannot use AGP's own `kotlin { android { } }` accessor.
Applying the plugin *is* what creates the Android target, so it has to happen from the module's own
declaration — and Gradle only generates type-safe accessors for plugins present in `plugins { }`
before the script is compiled. Everything else about a late-applied AGP is fine: the full task graph
is registered, and a source set declared before the target arrives is adopted rather than orphaned.

Repeat calls are idempotent — KGP's target factories are configure-or-create — so `default()`
followed by `android { }` refines the target rather than creating a second one.

**Narrowing is a dependency-graph decision, not a local one.** A module's target set must still
cover every platform its consumers need. `bkp.kmp.feature` modules pull in lifecycle ViewModel and
Koin via the [role baseline](#role-dependency-baselines), and `:app:shared` feeds `:app:android`,
`:app:desktop` and `:app:web`, so narrowing works bottom-up through the dependency closure. Dropping a platform
from an upstream library while a downstream module still targets it fails at resolution, not at
declaration.

**`web()` is why project isolation is off.** `wasmJs { browser() }` needs KGP's
`WasmNpmResolverPlugin`, which applies `WasmNodeJsRootPlugin` to the *root* project — reaching
across project boundaries, which isolation forbids. There is no per-module opt-out, so
`org.gradle.unsafe.isolated-projects` is unset in `gradle.properties`. Deleting every `web()` call
is what it would take to turn isolation back on; the configuration cache is unaffected either way.

### What validation can and cannot see

`bkp.validation.graph` compares the module's final target set against what `bkpTargets` recorded,
skipping KGP's automatic `metadata` target. It fails when a module declares nothing, and when a
target exists that was never declared.

It cannot flag a *redundant* direct call. KGP's factories are configure-or-create, so a build script
calling `iosArm64()` on an already-declared target neither creates anything nor leaves a trace —
it is indistinguishable from the declaration itself. Only the final set is observable.

## Validation lifecycle

Validation is split into two layers:

1. Apply-time wiring: primary plugins wire baseline tooling/dependencies in `apply()`.
2. Root graph checks: the root-applied `bkp.validation.graph` registers `checkModuleGraph` and
   `checkHelixPolicySync`. Cross-project models are reduced to plain node/edge records after project
   evaluation; task actions evaluate those records and scan declared source-file inputs without
   retaining `Project` references.

`afterEvaluate` is required, not incidental — the values being checked come from the `bkpModule`
block in the module's build script, which runs after plugins apply. Validation is per project
rather than a walk over `rootProject.allprojects` from `gradle.projectsEvaluated`, because project
isolation forbids reading another project's model; the per-project form also fails at the offending
module instead of after the whole build has configured.

Local configuration still fails when:

- `demoProdFlavors()` is called outside a `bkp.android.app*` module
- `bkp.android.app.firebase` is applied without a `bkp.android.app*` primary
- a KMP module declares no targets
- a KMP module has a target that was created outside `bkpTargets { }`

The root graph tasks enforce these stable rules:

| Rule | Enforcement |
|---|---|
| `MOD-ROLE-MISSING`, `MOD-ROLE-MULTIPLE` | Exactly one recorded Helix role |
| `MOD-PATH-ROLE-MISMATCH` | Role-to-path table from the adoption plan |
| `DEP-ROLE-DENIED` | Default-deny matrix in `config/helix/dependency-policy.json` |
| `DEP-FEATURE-FEATURE-PUBLIC-PRESENTATION-ONLY` | Feature imports from a peer stay below `.api` |
| `FEATURE-PUBLIC-SURFACE-OUTSIDE-API` | Feature top-level public declarations live in `api/` |
| `GRAPH-CYCLE-PHYSICAL`, `GRAPH-CYCLE-LOGICAL` | Project cycles before/after API/Impl family collapse |
| `EXC-EXPIRED` | Time-bounded exception registry hygiene |
| `POLICY-DRIFT` | Derived policy equals the marked master-source JSON |

Findings use `[RULE-ID] subject — problem. Fix: remedy` and the graph task always writes
`build/reports/helix/module-graph.json`. Every module with a build script is role-validated —
grandfathering was removed in phase 4. Two carve-outs remain, both inside the role matrix rather than
around it: `:testkit:*` may be consumed from any role, and an `app` → `app` edge is allowed because
`:app:shared` and the target shells are one composition root spread across four modules
(master source §8.2); `GRAPH-CYCLE-PHYSICAL` still holds those shells to a DAG. `:tooling:*` sits
outside the runtime graph, and structural parents such as `:app` carry no build script, so neither
reaches the role rules.

The source-backed rules (`FEATURE-PUBLIC-SURFACE-OUTSIDE-API` and the peer-import rule) take the
modules' `src` **directories** as task inputs and walk them while the task runs. Handing over a file
tree resolved at configuration time instead would freeze the file list into the configuration-cache
entry, and a public declaration added in a brand-new file would go unseen until something else forced
reconfiguration.

### Architecture exceptions

`config/helix/exceptions.json` stores `rule`, exact `scope`, `owner`, `reason`, `created`, `expires`,
and `removalCondition`. A matching current exception turns that finding into a warning. An expired
entry always fails with `EXC-EXPIRED`; blanket suppression is intentionally unavailable.

## Verification tiers

- `./gradlew verifyFast` runs JVM/common compile and tests, `detektAll`, `spotlessCheck`,
  `checkModuleGraph`, and `checkHelixPolicySync`. Included-build convention tests are excluded.
- `./gradlew verifyFull` adds Android `assembleDebug`, both web development/production executable
  distributions, and the shared app's debug iOS-simulator framework link.

Each umbrella task prints the chosen tier when it starts. The target task lookup is lazy so it
remains compatible with plugins that register tasks late and with the configuration cache.

## Tests

```bash
./gradlew -p build-logic :convention:test
```

The suite drives the plugins through Gradle TestKit against throwaway single- and multi-module
projects. `TestProject.withModule` creates nested projects and source fixtures for dependency,
cycle, exception, and path-rule tests. The
plugins are injected with `withPluginClasspath()` rather than resolved from a repository, which is
why `build.gradle.kts` lists AGP, KGP and friends a second time under `testPluginClasspath` — they
are `compileOnly` for publishing, but a TestKit build has no consumer to bring them. The synthetic
projects read this repo's real `gradle/libs.versions.toml`, so a plugin asking for a catalog key
that does not exist fails a test rather than only failing a real build.

## Quality plugins

Quality plugins are internal and auto-applied by primary plugins:

- `bkp.quality.style` (Spotless + detekt). Also applied to the root project, which has no sources
  but owns build scripts worth formatting; the plugin skips its detekt half there.
- `bkp.quality.lint` (Android lint when an Android or KMP Android plugin is present)

## Role dependency baselines

The convention core owns reusable bundles in
[`DependencyBundles.kt`](convention/src/main/kotlin/dev/mayankmkh/basekmpproject/convention/core/DependencyBundles.kt).

`bkp.kmp.feature` and `bkp.kmp.app` add lifecycle ViewModel/ViewModel Compose plus the Koin BOM,
core, and Compose ViewModel integration. `bkp.kmp.capability.impl` adds only the Koin BOM and core.
No role plugin adds project-to-project dependencies: since phase 3 every module names its own
project dependencies, and the old `FeatureBundle` is gone along with `bkp.kmp.lib.compose` and
`bkp.kmp.feature.compose`.

### Compose bundle

Configured centrally in `SharedCompose.kt` for compose-enabled primary plugins.

### Test bundle

Kotlin/Android test dependencies are configured by primary plugin type.

## Known gaps

- `bkp.android.lib` and `bkp.android.test` do not register `bkpModule`, so the DSL is unavailable
  in those modules and the validator's DSL checks short-circuit for them. The "one primary plugin"
  check still runs. Neither plugin has a consumer yet.
- The KMP primaries call `withHostTest` and `withDeviceTestBuilder` eagerly, so that `commonTest`
  gets an Android compilation instead of silently skipping the Android target. AGP creates those
  compilations once, so a module cannot call either again to refine its own test settings — it
  fails with an "already created" error. A per-module opt-out would go through `beforeVariants`.
- There is no test coverage for any of the above.
- New modules are scaffolded by `tooling/helix-kmp/helix-kmp create`, which renders hand-written
  templates and inserts the module into `settings.gradle.kts`. The templates live in
  `tooling/helix-kmp/templates/` and were derived by hand from `:feature:posts` and
  `:capability:posts-*`; nothing generates them from the plugins, so a change to a role plugin's
  defaults can leave them stale. `tooling/helix-kmp/tests/run-tests.sh` is what catches that — it
  scaffolds throwaway modules and runs `jvmTest`, `spotlessCheck`, `detektAll`, `checkModuleGraph`
  and `checkHelixPolicySync` against them. It is deliberately not part of `verifyFast`.
