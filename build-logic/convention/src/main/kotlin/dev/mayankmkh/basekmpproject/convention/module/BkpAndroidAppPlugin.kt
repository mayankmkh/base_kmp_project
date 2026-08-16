package dev.mayankmkh.basekmpproject.convention.module

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
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
                testOptions.animationsDisabled = true
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
                "implementation"(platform(libs.findLibrary("koin.bom").get()))
                "implementation"(libs.findLibrary("koin.android").get())
            }
        }
    }
}
