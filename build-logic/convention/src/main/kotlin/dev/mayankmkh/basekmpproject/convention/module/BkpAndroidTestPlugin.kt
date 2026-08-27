package dev.mayankmkh.basekmpproject.convention.module

import com.android.build.api.dsl.TestExtension
import dev.mayankmkh.basekmpproject.configureKotlinAndroid
import dev.mayankmkh.basekmpproject.convention.core.androidSdkConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class BkpAndroidTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 has built-in Kotlin support; `org.jetbrains.kotlin.android` must not be
            // applied.
            apply(plugin = "com.android.test")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.quality.lint")
            apply(plugin = "bkp.validation.graph")

            val sdk = androidSdkConfig()
            extensions.configure<TestExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = sdk.targetSdk
            }
        }
    }
}
