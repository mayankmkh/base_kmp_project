package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKotlinJvm
import dev.mayankmkh.basekmpproject.convention.dsl.bkpModuleExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

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

            bkpModuleExtension()

            configureKotlinJvm()

            dependencies {
                // The Compose plugins above only bring the compiler; without this a `@Preview` in
                // the desktop entry point does not resolve. The renderer (`ui-tooling`) is not
                // needed -- desktop previews render in the IDE from the annotation alone.
                "implementation"(libs.findLibrary("compose.ui.tooling.preview").get())
            }
        }
    }
}
