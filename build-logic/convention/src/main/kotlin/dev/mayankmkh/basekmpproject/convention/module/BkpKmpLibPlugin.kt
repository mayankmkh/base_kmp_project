package dev.mayankmkh.basekmpproject.convention.module

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.mayankmkh.basekmpproject.configureKotlinMultiplatformAndroidLibrary
import dev.mayankmkh.basekmpproject.convention.dsl.bkpModuleExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class BkpKmpLibPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "com.android.kotlin.multiplatform.library")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.quality.lint")
            apply(plugin = "bkp.validation.graph")

            val bkpModule = bkpModuleExtension().apply {
                targets.android.convention(true)
                targets.jvm.convention(true)
                targets.ios.convention(true)
                features.cocoapods.convention(false)
            }

            extensions.configure<KotlinMultiplatformExtension> {
                if (bkpModule.targets.ios.get()) {
                    iosArm64()
                    iosSimulatorArm64()
                }
                if (bkpModule.targets.jvm.get()) {
                    jvm()
                }
                if (bkpModule.targets.android.get()) {
                    // AGP 9 registers the KMP Android target as an `android` extension on the
                    // `kotlin` extension (AGP 8's `androidLibrary` name is deprecated) and no
                    // longer ships a typed accessor for use outside of build scripts, so the
                    // extension is looked up by name.
                    val androidTarget = (this as ExtensionAware).extensions
                        .getByName("android") as KotlinMultiplatformAndroidLibraryTarget
                    configureKotlinMultiplatformAndroidLibrary(androidTarget)
                }
            }

            dependencies {
                "commonTestImplementation"(libs.findLibrary("kotlin.test").get())
            }
        }
    }
}
