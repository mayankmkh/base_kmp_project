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
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.quality.lint")
            apply(plugin = "bkp.validation.graph")

            bkpModuleExtension().apply {
                features.flavorsDemoProd.convention(true)
                features.firebase.convention(false)
            }
            val sdk = androidSdkConfig()

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = sdk.targetSdk
                testOptions.animationsDisabled = true
                configureFlavors(this)
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                configurePrintApksTask(this)
            }

            dependencies {
                "implementation"(platform(libs.findLibrary("koin.bom").get()))
                "implementation"(libs.findLibrary("koin.android").get())
            }
        }
    }
}
