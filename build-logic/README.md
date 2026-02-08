### Original code at [NowInAndroid](https://github.com/android/nowinandroid/tree/a77d7b69b58238e2b5e934ab9e492c0932bf2904/build-logic)

# Convention Plugins (V3.1)

`build-logic` is an included build (configured in `settings.gradle.kts`) that publishes the convention plugins used by all modules.

## Primary plugin model

Each module must apply **exactly one** primary `bkp.*` plugin. The `bkp.validation.graph` plugin enforces this at configuration time (`gradle.projectsEvaluated`).

### Plugin selection guide
- Pure Android app: `bkp.android.app` or `bkp.android.app.compose`
- Pure Android lib: `bkp.android.lib`
- Android test module: `bkp.android.test`
- KMP library (no feature bundle): `bkp.kmp.lib` or `bkp.kmp.lib.compose`
- KMP feature (with shared feature deps): `bkp.kmp.feature` or `bkp.kmp.feature.compose`
- Desktop app: `bkp.desktop.app`

## `bkpModule` DSL

Primary plugins expose `bkpModule { ... }` for structured module configuration.

### Firebase boundary
- `bkpModule.features.firebase=true` requires applying `bkp.android.app.firebase`.
- `bkp.android.app.firebase` is valid only with `bkp.android.app*` primary plugins.

### CocoaPods boundary
- `bkpModule.features.cocoapods=true` and `bkpModule.cocoapods { ... }` provide convention defaults only.
- Convention defaults include shared values (`frameworkBaseName`, `iosDeploymentTarget`, `podfilePath`).
- Apply `org.jetbrains.kotlin.native.cocoapods` (`kotlinCocoapods` alias) in modules that enable CocoaPods.
- Module-specific CocoaPods settings stay in module scripts, e.g.:
  - `version`, `summary`, `homepage`
  - `framework { export(...) }`
  - any module-specific pods wiring

## Validation lifecycle

Validation is split into two phases:
1. Apply-time wiring: primary plugins wire baseline tooling/dependencies in `apply()`.
2. Post-configuration checks: `bkp.validation.graph` runs in `gradle.projectsEvaluated` to validate final DSL values and plugin graph.

This keeps validation early (before task execution) and configuration-cache compatible.

## Quality plugins

Quality plugins are internal and auto-applied by primary plugins:
- `bkp.quality.style` (Spotless + Detekt)
- `bkp.quality.lint` (Android lint when Android/KMP Android plugin is present)

## Dependency bundles inventory

The convention core owns reusable bundles in `DependencyBundles.kt`.

### FeatureBundle (used by `bkp.kmp.feature*`)
- `:shared:libs:arch:core`
- `:shared:libs:coroutines-x`
- `:shared:libs:networking`
- `:shared:libs:designsystem`
- Decompose + Essenty lifecycle coroutines
- Koin BOM + Koin core

### Compose bundle
Configured centrally in `SharedCompose.kt` for compose-enabled primary plugins.

### Test bundle
Kotlin/Android test dependencies are configured by primary plugin type.
