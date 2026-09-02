package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.convention.core.registerVerifyFullModule
import dev.mayankmkh.basekmpproject.convention.dsl.BkpTargets
import dev.mayankmkh.basekmpproject.convention.helix.HelixRole
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
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
            applyRoleBase(HelixRole.APP)
            applyCompose()
            registerVerifyFullModule { name -> name in FULL_TIER_TASK_NAMES }
            serializeExecutableVariants()

            val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
            (kotlin as ExtensionAware).extensions.getByType<BkpTargets>().web {
                browser { commonWebpackConfig { outputFileName = BUNDLE_FILE_NAME } }
            }
        }
    }

    /**
     * The Kotlin plugin syncs the development and the production executable into the same
     * `build/wasm/packages/<module>/kotlin` directory, and each variant's webpack task reads from
     * there. With both variants in one task graph -- which `verifyFull` asks for -- Gradle rejects
     * the undeclared cross-variant dependency, and without an order the development bundle could be
     * built from production output. Running every production task after every development task
     * keeps each variant's sync and bundle adjacent.
     */
    private fun Project.serializeExecutableVariants() {
        val development = tasks.matching { it.name.isWasmJsVariant("Development") }
        tasks
            .matching {
                it.name.isWasmJsVariant("Production") || it.name == PRODUCTION_DISTRIBUTION
            }
            .configureEach {
                mustRunAfter(development)
            }
    }

    private fun String.isWasmJsVariant(variant: String) = startsWith("wasmJs") && variant in this

    private companion object {
        /**
         * The production distribution carries no variant in its name, unlike its development twin,
         * so the four full-tier tasks are listed rather than pattern-matched.
         */
        const val PRODUCTION_DISTRIBUTION = "wasmJsBrowserDistribution"
        val FULL_TIER_TASK_NAMES =
            setOf(
                "wasmJsBrowserDevelopmentWebpack",
                "wasmJsBrowserDevelopmentExecutableDistribution",
                "wasmJsBrowserProductionWebpack",
                PRODUCTION_DISTRIBUTION,
            )

        /**
         * Webpack otherwise names the bundle after the Gradle project, so `:app:web` would emit
         * `web.js` and a rename of the module would silently break `index.html`. Pinning the name
         * keeps the `<script src>` a constant, and keeps it distinct enough to sit in a host page's
         * asset directory without colliding.
         */
        const val BUNDLE_FILE_NAME = "webApp.js"
    }
}
