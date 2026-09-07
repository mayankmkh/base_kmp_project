# Build environments

- **Status:** Accepted 2026-09-07
- **Owner:** mayankmkh@gmail.com

This is the single source for how a build chooses which deployment it talks to. The decision and
its alternatives are [ADR-46](decisions.md#adr-46---the-environment-is-a-build-fact-each-platform-carries-in-its-own-way);
this document owns the mechanics.

## 1. What an environment is

Two environments ship: **staging** and **production**. An environment is a *build fact*, not a
runtime setting — no screen, preference or debug menu changes it, and nothing below the composition
root can read it.

It is independent of the debug/release axis. A staging build can be a release build (that is what a
staging TestFlight or an internal Android track is), and either environment can be built in debug.
The two signals answer different questions:

| Signal | Question | Owner |
| --- | --- | --- |
| `isDebug` | Was this built for development? | the platform's build type |
| `BuildEnvironment` | Which deployment does it talk to? | the platform's environment mechanism |

## 2. Ownership

| Fact | Owner |
| --- | --- |
| The set of environments and their ids | `BuildEnvironment` in `app/shared/.../config/BuildEnvironment.kt` |
| What each environment *means* — base URL, application id | the extension properties in that same file |
| Which environment a given build is | each platform's entry point, from its own build system |
| How the environment reaches the graph | the `app.environment` Koin property, read by `AppEnvironment` |

`BuildEnvironment` carries an `id` — `staging`, `prod` — because every platform mechanism below can
only carry text across the boundary into Kotlin. `BuildEnvironment.fromId` maps it back and
**fails loudly** on anything unrecognised: a typo in a flavor name or an xcconfig must never
silently point a build at the wrong backend.

The two derived properties are exhaustive `when`s, so adding a third environment fails to compile
until it has been given a host and an id rather than inheriting whatever the default was.

## 3. Selection, per platform

Each entry point reads the environment from the mechanism its own platform already has, exactly as
it reads `isDebug` today. There is no shared Gradle property and no code generation.

| Platform | Mechanism | Where it is written |
| --- | --- | --- |
| Android | product flavor on the `environment` dimension → `BuildConfig.APP_ENVIRONMENT` | `BkpFlavor.kt`, enabled by `environmentFlavors()` |
| iOS | Xcode build configuration → xcconfig → `Info.plist` `AppEnvironment` → Swift | `app/ios/Configuration/*.xcconfig` |
| Desktop | Gradle property → JVM system property `bkp.environment` | `app/desktop/build.gradle.kts` |
| Web | host page `<meta name="app-environment">` | `app/web/src/wasmJsMain/resources/index.html` |

Android is the only platform that can build both environments in one invocation; that is what
flavors are for. The other three select one per build or per page.

### Android

`environmentFlavors()` registers `staging` and `prod` on an `environment` dimension. Four variants
result, all meaningful: `stagingDebug`, `stagingRelease`, `prodDebug`, `prodRelease`. The flavor
name *is* the environment id, and `staging` carries an `applicationIdSuffix` of `.staging`.

```bash
./gradlew :app:android:installStagingDebug
./gradlew :app:android:installProdDebug
```

### iOS

Four build configurations — `Debug-Staging`, `Debug-Production`, `Release-Staging`,
`Release-Production` — and two shared schemes, `iosApp-Staging` and `iosApp-Production`, which run
and test against the `Debug-*` configuration and profile and archive against the `Release-*` one.
Pick the scheme in Xcode; nothing else changes.

The xcconfig files layer rather than repeat:

```text
Config.xcconfig              team, product name, versions, BASE_BUNDLE_IDENTIFIER, EXCLUDED_ARCHS
├── Staging.xcconfig         APP_ENVIRONMENT, .staging bundle id and display name
│   ├── Debug-Staging        KOTLIN_FRAMEWORK_BUILD_TYPE = debug
│   └── Release-Staging      KOTLIN_FRAMEWORK_BUILD_TYPE = release
└── Production.xcconfig      APP_ENVIRONMENT; identity unchanged from Config
    ├── Debug-Production     KOTLIN_FRAMEWORK_BUILD_TYPE = debug
    └── Release-Production   KOTLIN_FRAMEWORK_BUILD_TYPE = release
```

Two settings exist only because the configurations are no longer named literally `Debug` and
`Release`:

- `KOTLIN_FRAMEWORK_BUILD_TYPE` tells the Kotlin Gradle plugin which Kotlin/Native binary
  `embedAndSignAppleFrameworkForXcode` should link. It reads `CONFIGURATION` first and only falls
  back to this variable, so a custom configuration name needs it. Xcode exports every build setting
  to the script phase as an environment variable, which is how it arrives.
- `FRAMEWORK_SEARCH_PATHS` already interpolates `$(CONFIGURATION)`, and the plugin writes to
  `build/xcode-frameworks/$CONFIGURATION/$SDK_NAME` verbatim, so it needed no change.

`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` is unrelated to environments but required by them:
`:app:shared` declares `iosArm64` and `iosSimulatorArm64` and no x86_64 slice, and a Release
configuration builds every architecture the SDK offers rather than only the active one.

### Desktop

Compose Desktop has neither flavors nor build configurations, so the environment is a Gradle
property the build script turns into a JVM system property carried by both the `run` task and every
packaged launcher:

```bash
./gradlew :app:desktop:run -Pbkp.environment=staging
./gradlew :app:desktop:packageDistributionForCurrentOS -Pbkp.environment=staging
```

Saying nothing builds production. Staging gets its own `packageName`, so a staging install does not
replace a production one.

### Web

One bundle is served from every deployment, so the environment belongs to the page that mounts it
rather than to webpack — which is also what `:app:web` needs once it is embedded in a host page:

```html
<meta name="app-environment" content="staging">
```

The tag is **required** and has no default. Any default would have to be production, and the page
likelier to forget the tag is a staging one — which would put testers on real production data with
nothing to indicate it. Refusing to start is the cheaper failure. This is the one value a host page
embedding the bundle has to supply.

## 4. What differs between them

| | Staging | Production |
| --- | --- | --- |
| API base URL | the staging host | the production host |
| Application id / bundle id / package name | suffixed `.staging` | bare |
| iOS display name | `… Staging` | unchanged |
| Ktor log level in a *release* build | `INFO` — method, URL, status | `NONE` |
| Kermit minimum severity in a *release* build | `Debug` | `Warn` |

The sample app has one real backend — JSONPlaceholder has no staging deployment — so both entries
in `apiBaseUrl` name it today. A fork replaces those two lines and nothing else.

Storage identity is the load-bearing difference. Both environments must be installable side by side
and must never read each other's databases, preferences or secrets, so the suffix runs all the way
through `PlatformContext` to the file names — see [`preferences.md`](preferences.md) §10.

The logging difference is deliberate: a staging release exists to be diagnosed, and its traffic is
not a user's. It logs the call timeline -- method, URL, status -- and no headers, because it is a
shipped artifact someone else is holding. That costs nothing you would debug from: `NetworkFailure`
carries its own request id and response body, seeded before the first attempt and preserved through
`tryCatching`, so failure diagnostics do not depend on this gate at all. Headers would only add the
ones on calls that succeeded. A production release logs nothing.

Header logging stays where the exposure is a developer's own machine: `isDebug` builds, in either
environment. Bodies are never logged at any level -- the plugin has to buffer one to print it --
and the credential-bearing headers are sanitised inside the client (see [`network.md`](network.md)).

Both rows move together. Kermit's minimum severity and Ktor's log level are two gates in series --
Ktor decides what to hand the adapter, Kermit decides what survives -- and `KermitKtorLogger` emits
at `Debug`. Raising one without the other produces a build that formats every header line and then
discards it, with nothing observable to say so. `AppEnvironmentTest` asserts the pair across all
four builds.

## 5. How it reaches the graph

`initKoin(isDebug, environment)` takes both build signals and passes them as Koin properties —
`app.isDebug` and `app.environment` — because a definition written inside the entry point would
make the module list dynamic and disable the Koin compiler plugin's graph-wide validation
(`helix-kmp-source-of-truth.md` §18.7).

`environmentModule` builds the one `AppEnvironment` from those two properties. Every gate derives
from it and nothing else: the base URL, the application id, Kermit's minimum severity, Koin's log
level and Ktor's. `KoinGraphTest` lists `BuildEnvironment` in its `extraTypes` for the same reason
`Boolean` is there — it arrives as a property, not as a definition.

## 6. Adding an environment

1. Add the entry to `BuildEnvironment` with its id.
2. Give it a base URL and an application id — the compiler will demand both.
3. Android: add the flavor to `BkpFlavor`.
4. iOS: add `<Name>.xcconfig` and its two `Debug-`/`Release-` leaves, two build configurations, and
   a scheme.
5. Desktop and web need nothing: they carry the id as text already.
