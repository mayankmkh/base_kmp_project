package dev.mayankmkh.basekmpproject.convention.module

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import dev.mayankmkh.basekmpproject.configureBuildTypes
import dev.mayankmkh.basekmpproject.configureFlavors
import dev.mayankmkh.basekmpproject.configureKotlinAndroid
import dev.mayankmkh.basekmpproject.configurePrintApksTask
import dev.mayankmkh.basekmpproject.convention.core.androidSdkConfig
import dev.mayankmkh.basekmpproject.convention.dsl.bkpModuleExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class BkpAndroidAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 has built-in Kotlin support; `org.jetbrains.kotlin.android` must not be applied.
            apply(plugin = "com.android.application")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.quality.lint")
            apply(plugin = "bkp.validation.graph")

            val bkpModule = bkpModuleExtension()
            val sdk = androidSdkConfig()

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = sdk.targetSdk
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.animationsDisabled = true
                configureBuildTypes(this)

                // Two licence files that several JVM libraries each ship a copy of; without this the
                // packager fails on the duplicates.
                packaging.resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")

                // Off by default since AGP 8. An app is where `BuildConfig.DEBUG` and the generated
                // build-type constants are actually read, so every app module wants it on.
                buildFeatures.buildConfig = true
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                configurePrintApksTask(this)

                // Flavors are read from `bkpModule { }`, which the module's build script evaluates
                // long after `apply()` returns -- reading the flag here would always see it unset.
                // `finalizeDsl` runs once the script has been evaluated but before AGP creates
                // variants, which is the last point at which flavors can still be registered, so
                // plain `afterEvaluate` is not an option.
                finalizeDsl { applicationExtension ->
                    if (bkpModule.features.demoProdFlavorsEnabled) {
                        configureFlavors(applicationExtension)
                    }
                }
            }

            dependencies {
                // The runner named in `testInstrumentationRunner` above ships in this artifact and
                // nothing else pulls it in, so without it a device test fails to start.
                "androidTestImplementation"(libs.findLibrary("androidx.test.runner").get())
                "implementation"(platform(libs.findLibrary("koin.bom").get()))
                "implementation"(libs.findLibrary("koin.android").get())
            }
        }
    }
}
