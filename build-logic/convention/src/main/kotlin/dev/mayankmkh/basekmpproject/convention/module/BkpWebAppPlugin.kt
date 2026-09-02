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
            registerVerifyFullModule { name ->
                name.startsWith("wasmJsBrowser") &&
                    ("Development" in name || "Production" in name) &&
                    (name.endsWith("Webpack") || name.endsWith("ExecutableDistribution"))
            }

            val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
            (kotlin as ExtensionAware).extensions.getByType<BkpTargets>().web {
                browser { commonWebpackConfig { outputFileName = BUNDLE_FILE_NAME } }
            }
        }
    }

    private companion object {
        /**
         * Webpack otherwise names the bundle after the Gradle project, so `:app:web` would emit
         * `web.js` and a rename of the module would silently break `index.html`. Pinning the name
         * keeps the `<script src>` a constant, and keeps it distinct enough to sit in a host page's
         * asset directory without colliding.
         */
        const val BUNDLE_FILE_NAME = "webApp.js"
    }
}
