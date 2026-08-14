package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKotlinJvm
import dev.mayankmkh.basekmpproject.convention.dsl.bkpModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class BkpDesktopAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Kotlin/JVM rather than multiplatform: this module is a single-target entry point with
            // no common code and no expect/actual, so the KMP plugin only bought a `desktopMain`
            // source set where `main` would do. Matches JetBrains' recommended structure.
            // https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "org.jetbrains.compose")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.validation.graph")

            bkpModuleExtension().apply {
                targets.android.convention(false)
                targets.jvm.convention(true)
                targets.ios.convention(false)
            }

            configureKotlinJvm()
        }
    }
}
