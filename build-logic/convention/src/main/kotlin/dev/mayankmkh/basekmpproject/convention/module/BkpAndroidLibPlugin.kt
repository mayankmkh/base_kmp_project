package dev.mayankmkh.basekmpproject.convention.module

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import dev.mayankmkh.basekmpproject.configureKotlinAndroid
import dev.mayankmkh.basekmpproject.configurePrintApksTask
import dev.mayankmkh.basekmpproject.convention.core.androidSdkConfig
import dev.mayankmkh.basekmpproject.convention.core.configureAndroidPowerAssert
import dev.mayankmkh.basekmpproject.disableUnnecessaryAndroidTests
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get

class BkpAndroidLibPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 has built-in Kotlin support; `org.jetbrains.kotlin.android` must not be applied.
            apply(plugin = "com.android.library")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.quality.lint")
            apply(plugin = "bkp.validation.graph")

            val sdk = androidSdkConfig()
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // AGP 9 removed `targetSdk` from the library `defaultConfig`; for libraries it only
                // ever affected tests and lint, and now lives on `testOptions`.
                testOptions.targetSdk = sdk.targetSdk
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.animationsDisabled = true
                namespace = "dev.mayankmkh.basekmpproject" + project.path.replace(':', '.').replace('-', '.')
            }
            extensions.configure<LibraryAndroidComponentsExtension> {
                configurePrintApksTask(this)
                disableUnnecessaryAndroidTests(target)
            }

            // The same test baseline `bkp.kmp.lib` gives `commonTest`, so an Android-only module is
            // not the odd one out. `kotlin-test` alone is not enough here: on JVM its annotations
            // are optional expectations that only a framework artifact supplies, and the variant
            // that picks one automatically is a KGP feature this module deliberately does without.
            dependencies {
                "androidTestImplementation"(libs.findLibrary("kotlin.test").get())
                "androidTestImplementation"(libs.findLibrary("kotlin.testJunit").get())
                "testImplementation"(libs.findLibrary("kotlin.test").get())
                "testImplementation"(libs.findLibrary("kotlin.testJunit").get())
                "testImplementation"(libs.findLibrary("kotlinx.coroutines.test").get())
                "testImplementation"(libs.findLibrary("turbine").get())
            }

            configureAndroidPowerAssert()
        }
    }
}
