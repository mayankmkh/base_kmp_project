plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.powerAssert) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.bkp.android.app) apply false
    alias(libs.plugins.bkp.android.app.compose) apply false
    alias(libs.plugins.bkp.android.app.firebase) apply false
    alias(libs.plugins.bkp.android.lib) apply false
    alias(libs.plugins.bkp.android.test) apply false
    alias(libs.plugins.bkp.kmp.lib) apply false
    alias(libs.plugins.bkp.kmp.lib.compose) apply false
    alias(libs.plugins.bkp.kmp.feature) apply false
    alias(libs.plugins.bkp.kmp.feature.compose) apply false
    alias(libs.plugins.bkp.desktop.app) apply false
    // Applied, not just declared: the root has no sources, but it owns `build.gradle.kts` and
    // `settings.gradle.kts`, and the plugin skips its detekt half for the root project.
    alias(libs.plugins.bkp.quality.style)
    alias(libs.plugins.bkp.quality.lint) apply false
    alias(libs.plugins.bkp.validation.graph) apply false
}
