package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.convention.dsl.BkpTargets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The browser entry point: a KMP module with exactly one target.
 *
 * Multiplatform for a single platform reads oddly next to `bkp.desktop.app`, which is plain
 * Kotlin/JVM for the same job. The difference is that there is no non-multiplatform way to compile
 * Kotlin to Wasm -- `wasmJs` is a KMP target and nothing else creates it.
 *
 * The target is declared here rather than left to the module. `bkpTargets` exists so a *library*
 * can choose its platforms; an app module named for the web has nothing to choose, and leaving the
 * declaration in the build script would only invite a second target that the web app cannot use.
 */
class BkpWebAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "bkp.kmp.lib.compose")

            val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
            (kotlin as ExtensionAware).extensions.getByType<BkpTargets>().web()
        }
    }
}
