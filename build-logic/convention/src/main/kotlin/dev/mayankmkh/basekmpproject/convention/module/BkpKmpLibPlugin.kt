package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKotlin
import dev.mayankmkh.basekmpproject.convention.dsl.BkpTargets
import dev.mayankmkh.basekmpproject.convention.dsl.bkpModuleExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class BkpKmpLibPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.quality.lint")
            apply(plugin = "bkp.validation.graph")

            bkpModuleExtension()

            val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
            (kotlin as ExtensionAware).extensions.create<BkpTargets>("bkpTargets", this, kotlin)

            // Unconditional: the compiler baseline has nothing to do with which platforms the module
            // picks, and hanging it off the Android target would drop warnings-as-errors and the
            // shared free compiler args from a module that omits Android.
            configureKotlin()

            // Every module in this project is coroutine-shaped, so `runTest` and `Flow` assertions
            // are as much a baseline as `kotlin.test` itself. Both publish for all of `bkpTargets`.
            dependencies {
                "commonTestImplementation"(libs.findLibrary("kotlin.test").get())
                "commonTestImplementation"(libs.findLibrary("kotlinx.coroutines.test").get())
                "commonTestImplementation"(libs.findLibrary("turbine").get())
            }
        }
    }
}
