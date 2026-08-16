### Original code at [NowInAndroid](https://github.com/android/nowinandroid/tree/a77d7b69b58238e2b5e934ab9e492c0932bf2904/build-logic)

# Convention Plugins

`build-logic` is an included build (configured in [`settings.gradle.kts`](../settings.gradle.kts))
that publishes the convention plugins used by all modules. It reads the same
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml) as the main build.

## Layout

```
convention/src/main/kotlin/dev/mayankmkh/basekmpproject/
├── convention/core/         catalog-backed config (BuildConfig) and shared dependency bundles
├── convention/dsl/          the bkpModule extension and its defaults
├── convention/module/       the primary plugins
├── convention/quality/      Spotless + detekt (style), Android lint (lint)
└── convention/validation/   plugin-graph and DSL legality checks
```

The files directly under `dev/mayankmkh/basekmpproject/` are helpers shared by the plugins
(`KotlinMultiplatform.kt`, `SharedCompose.kt`, `BkpFlavor.kt`, …).

## Primary plugin model

Each module must apply **exactly one** primary `bkp.*` plugin. It brings the underlying tool
plugins, the shared configuration, and the quality and validation plugins with it — modules never
apply Spotless, detekt, lint, or the validator by hand.

### Plugin selection guide

| Module is a… | Plugin | Notes |
|---|---|---|
| Android app | `bkp.android.app` | flavors on by default |
| Android app with Compose | `bkp.android.app.compose` | adds `bkp.android.app` |
| Android library (non-KMP) | `bkp.android.lib` | no consumers today |
| Android test module | `bkp.android.test` | |
| KMP library (no feature bundle) | `bkp.kmp.lib` | Android + JVM + iOS |
| KMP library with Compose | `bkp.kmp.lib.compose` | adds `bkp.kmp.lib` |
| KMP feature (with shared feature deps) | `bkp.kmp.feature` | `bkp.kmp.lib` + the feature bundle |
| KMP feature with Compose | `bkp.kmp.feature.compose` | adds `bkp.kmp.feature` |
| Desktop app | `bkp.desktop.app` | Kotlin/JVM, not KMP |

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
- **KMP primaries** declare `iosArm64`, `iosSimulatorArm64`, `jvm()` and the AGP KMP Android
  library target, and add `kotlin-test` to `commonTest`.
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
    features.flavorsDemoProd.set(true)
}
```

| Property | Default | Valid on |
|---|---|---|
| `targets.android` | `true` on KMP primaries, `false` on `bkp.desktop.app` | `bkp.kmp.*` |
| `targets.jvm` | `true` on KMP primaries and `bkp.desktop.app` | `bkp.kmp.*` |
| `targets.ios` | `true` on KMP primaries, `false` on `bkp.desktop.app` | `bkp.kmp.*` |
| `features.flavorsDemoProd` | `true` | `bkp.android.app*` |
| `features.firebase` | `true` when `bkp.android.app.firebase` is applied, else `false` | `bkp.android.app*` |

`features.flavorsDemoProd` is honoured: setting it to `false` leaves the app on plain
`debug`/`release` variants. The flag is read in AGP's `finalizeDsl`, which runs after the module's
`bkpModule` block but still before variants are created — the last point at which flavors can be
registered. Reading it in `apply()` would always see the convention, and `afterEvaluate` is too
late for DSL changes. Anything that needs the flavors to exist while the build script is still
being evaluated (a `demoImplementation` dependency, a `productFlavors { }` block of its own) will
not find them; no module does that today.

**Target overrides do not work yet**, and `bkp.validation.graph` fails the build if a KMP module
turns one off. The check guards a real ordering problem rather than merely an untested path: the
KMP primaries read `targets.*` inside `apply()`, which runs *before* the module's `bkpModule` block
is evaluated, so `targets.ios.set(false)` in a build script does not change which targets get
declared. Dropping the check would silently ignore the override instead of honouring it.
Implementing overrides means deferring target declaration until after the extension is configured.

### Firebase boundary

- `bkpModule.features.firebase=true` requires applying `bkp.android.app.firebase`.
- `bkp.android.app.firebase` is valid only with `bkp.android.app*` primary plugins.

## Validation lifecycle

Validation is split into two phases:

1. Apply-time wiring: primary plugins wire baseline tooling/dependencies in `apply()`.
2. Post-configuration checks: `bkp.validation.graph` validates the final DSL values and plugin
   graph in the module's own `afterEvaluate`.

`afterEvaluate` is required, not incidental — the values being checked come from the `bkpModule`
block in the module's build script, which runs after plugins apply. Validation is per project
rather than a walk over `rootProject.allprojects` from `gradle.projectsEvaluated`, because project
isolation forbids reading another project's model; the per-project form also fails at the offending
module instead of after the whole build has configured.

The build fails when:

- more than one primary plugin group is applied
- `bkpModule` is present but no primary plugin is
- `flavorsDemoProd` or `firebase` is set outside a `bkp.android.app*` module
- `bkp.android.app.firebase` is applied without a `bkp.android.app*` primary
- `features.firebase` is enabled but `bkp.android.app.firebase` is not applied
- a KMP module disables any of `targets.android` / `jvm` / `ios`
- `targets.*` is set on a non-KMP, non-desktop primary

## Quality plugins

Quality plugins are internal and auto-applied by primary plugins:

- `bkp.quality.style` (Spotless + detekt). Also applied to the root project, which has no sources
  but owns build scripts worth formatting; the plugin skips its detekt half there.
- `bkp.quality.lint` (Android lint when an Android or KMP Android plugin is present)

## Dependency bundles inventory

The convention core owns reusable bundles in
[`DependencyBundles.kt`](convention/src/main/kotlin/dev/mayankmkh/basekmpproject/convention/core/DependencyBundles.kt).

### FeatureBundle (used by `bkp.kmp.feature*`)

- `:shared:libs:arch:core`
- `:shared:libs:coroutines-x`
- `:shared:libs:networking`
- `:shared:libs:designsystem`
- Decompose + Essenty lifecycle coroutines
- Koin BOM + Koin core

This is the only difference between `bkp.kmp.lib` and `bkp.kmp.feature`.

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
- There is no module generator; new modules are written by hand.
