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
    alias(libs.plugins.koin.compiler) apply false
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
    alias(libs.plugins.bkp.kmp.feature) apply false
    alias(libs.plugins.bkp.kmp.app) apply false
    alias(libs.plugins.bkp.kmp.ui) apply false
    alias(libs.plugins.bkp.kmp.capability.api) apply false
    alias(libs.plugins.bkp.kmp.capability.impl) apply false
    alias(libs.plugins.bkp.kmp.foundation.api) apply false
    alias(libs.plugins.bkp.kmp.foundation.runtime) apply false
    alias(libs.plugins.bkp.kmp.platform) apply false
    alias(libs.plugins.bkp.kmp.platform.api) apply false
    alias(libs.plugins.bkp.kmp.platform.impl) apply false
    alias(libs.plugins.bkp.kmp.storage) apply false
    alias(libs.plugins.bkp.kmp.testkit) apply false
    alias(libs.plugins.bkp.desktop.app) apply false
    alias(libs.plugins.bkp.web.app) apply false
    // Applied, not just declared: the root has no sources, but it owns `build.gradle.kts` and
    // `settings.gradle.kts`, and the plugin skips its detekt half for the root project.
    alias(libs.plugins.bkp.quality.style)
    alias(libs.plugins.bkp.quality.lint) apply false
    alias(libs.plugins.bkp.validation.graph)
}

// `named`, not `matching`: both tolerate a subproject that registers no such lifecycle -- parent
// projects without a build script do not -- and both stay live, but `matching` takes a `Spec<Task>`
// and so instantiates every task in every subproject to ask it its name. Same rule the convention
// plugins follow; `androidx.lint:lint-gradle` enforces it there but does not see this script.
val verifyFastModules = subprojects.map { candidate ->
    candidate.tasks.named { it == "verifyFastModule" }
}

val verifyFast =
    tasks.register("verifyFast") {
        group = "verification"
        description = "Runs the Helix JVM/common inner-loop verification tier."
        dependsOn(
            verifyFastModules,
            "spotlessCheck",
            "checkModuleGraph",
        )
    }

val verifyFullModules = subprojects.map { candidate ->
    candidate.tasks.named { it == "verifyFullModule" }
}

val verifyFull =
    tasks.register("verifyFull") {
        group = "verification"
        description = "Runs the Helix full supported-target verification tier."
        dependsOn(
            verifyFastModules,
            verifyFullModules,
            "spotlessCheck",
            "checkModuleGraph",
        )
    }

gradle.taskGraph.whenReady {
    when {
        hasTask(verifyFull.get()) -> logger.lifecycle("Helix verification tier: FULL")
        hasTask(verifyFast.get()) -> logger.lifecycle("Helix verification tier: FAST")
    }
}
