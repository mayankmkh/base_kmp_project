### Original code at [NowInAndroid](https://github.com/android/nowinandroid/tree/a77d7b69b58238e2b5e934ab9e492c0932bf2904/build-logic)

# Convention Plugins

The `build-logic` folder defines project-specific convention plugins, used to keep a single
source of truth for common module configurations.

This approach is heavily based on
[https://developer.squareup.com/blog/herding-elephants/](https://developer.squareup.com/blog/herding-elephants/)
and
[https://github.com/jjohannes/idiomatic-gradle](https://github.com/jjohannes/idiomatic-gradle).

By setting up convention plugins in `build-logic`, we can avoid duplicated build script setup,
messy `subproject` configurations, without the pitfalls of the `buildSrc` directory.

`build-logic` is an included build, as configured in the root
[`settings.gradle.kts`](../settings.gradle.kts).

Inside `build-logic` is a `convention` module, which defines a set of plugins that all normal
modules can use to configure themselves.

`build-logic` also includes a set of `Kotlin` files used to share logic between plugins themselves,
which is most useful for configuring Android components (libraries vs applications) with shared
code.

These plugins are *additive* and *composable*, and try to only accomplish a single responsibility.
Modules can then pick and choose the configurations they need.
If there is one-off logic for a module without shared code, it's preferable to define that directly
in the module's `build.gradle`, as opposed to creating a convention plugin with module-specific
setup.

Current list of convention plugins:

**Android Application Plugins:**
- [`basekmpproject.android.application`](convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt): Configures common Android application options and Kotlin settings
- [`basekmpproject.android.application.compose`](convention/src/main/kotlin/AndroidApplicationComposeConventionPlugin.kt): Configures Jetpack Compose options for Android applications
- [`basekmpproject.android.application.firebase`](convention/src/main/kotlin/AndroidApplicationFirebaseConventionPlugin.kt): Configures Firebase services (Analytics, Performance, Crashlytics) for Android applications
- [`basekmpproject.android.application.flavors`](convention/src/main/kotlin/AndroidApplicationFlavorsConventionPlugin.kt): Configures build flavors for Android applications

**Android Library Plugins:**
- [`basekmpproject.android.library`](convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt): Configures common Android library options and Kotlin settings
- [`basekmpproject.android.library.compose`](convention/src/main/kotlin/AndroidLibraryComposeConventionPlugin.kt): Configures Jetpack Compose options for Android libraries
- [`basekmpproject.android.feature`](convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt): Configures Android feature modules with common dependencies and serialization

**Android Testing & Quality Plugins:**
- [`basekmpproject.android.test`](convention/src/main/kotlin/AndroidTestConventionPlugin.kt): Configures Android testing options and dependencies
- [`basekmpproject.android.lint`](convention/src/main/kotlin/AndroidLintConventionPlugin.kt): Configures Android Lint with XML/SARIF reporting and dependency checking

**Kotlin Multiplatform Plugins:**
- [`basekmpproject.shared.library`](convention/src/main/kotlin/SharedLibraryConventionPlugin.kt): Configures Kotlin Multiplatform shared libraries with common dependencies
- [`basekmpproject.shared.library.compose`](convention/src/main/kotlin/SharedLibraryComposeConventionPlugin.kt): Configures Compose Multiplatform for shared libraries
- [`basekmpproject.shared.feature`](convention/src/main/kotlin/SharedFeatureConventionPlugin.kt): Configures shared feature modules with common dependencies and serialization

**JVM Library Plugin:**
- [`basekmpproject.jvm.library`](convention/src/main/kotlin/JvmLibraryConventionPlugin.kt): Configures JVM libraries with Kotlin and testing setup

**Code Quality Plugin:**
- [`basekmpproject.style.enforcer`](convention/src/main/kotlin/StyleEnforcerConventionPlugin.kt): Configures code style enforcement (includes Spotless and Detekt setup)
