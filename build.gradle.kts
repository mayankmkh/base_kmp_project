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

val announceVerifyFast =
    tasks.register("announceVerifyFast") {
        group = "verification"
        description = "Prints the selected Helix verification tier."
        doLast { logger.lifecycle("Helix verification tier: FAST") }
    }

val verifyFast =
    tasks.register("verifyFast") {
        group = "verification"
        description = "Runs the Helix JVM/common inner-loop verification tier."
        dependsOn(announceVerifyFast, "checkModuleGraph", "checkHelixPolicySync")
    }

val announceVerifyFull =
    tasks.register("announceVerifyFull") {
        group = "verification"
        description = "Prints the selected Helix verification tier."
        doLast { logger.lifecycle("Helix verification tier: FULL") }
    }

val verifyFull =
    tasks.register("verifyFull") {
        group = "verification"
        description = "Runs the Helix full supported-target verification tier."
        dependsOn(announceVerifyFull, "checkModuleGraph", "checkHelixPolicySync")
    }

// AGP/KMP register some tasks late, so task names are captured after every project is evaluated and
// wired through lazy providers. Included build-logic tests are outside `allprojects`, so the fast
// tier never recurses into convention TestKit tests.
gradle.projectsEvaluated {
    allprojects.forEach { candidate ->
        val candidatePath = candidate.path
        candidate.tasks.configureEach {
            if (name != "announceVerifyFast" && name != "announceVerifyFull") {
                mustRunAfter(announceVerifyFast, announceVerifyFull)
            }
        }
        val fastTaskNames =
            candidate.tasks.names.filter { name ->
                name == "jvmTest" ||
                    name == "test" ||
                    name == "compileCommonMainKotlinMetadata" ||
                    name == "compileKotlinMetadata" ||
                    name == "detektAll" ||
                    name == "spotlessCheck"
            }
        verifyFast.configure {
            dependsOn(fastTaskNames.map(candidate.tasks::named))
        }
        verifyFull.configure {
            dependsOn(fastTaskNames.map(candidate.tasks::named))
        }
        val fullTaskNames =
            candidate.tasks.names.filter { name ->
                val isAndroidDebug = candidatePath == ":app:android" && name == "assembleDebug"
                val isWebExecutable =
                    candidatePath == ":app:web" &&
                        name.startsWith("wasmJsBrowser") &&
                        ("Development" in name || "Production" in name) &&
                        (name.endsWith("Webpack") || name.endsWith("ExecutableDistribution"))
                val isIosSimulatorFramework =
                    candidatePath == ":app:shared" && name == "linkDebugFrameworkIosSimulatorArm64"
                isAndroidDebug || isWebExecutable || isIosSimulatorFramework
            }
        verifyFull.configure {
            dependsOn(fullTaskNames.map(candidate.tasks::named))
        }
    }
}
