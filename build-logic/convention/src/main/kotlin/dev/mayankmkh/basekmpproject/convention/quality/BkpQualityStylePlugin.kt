package dev.mayankmkh.basekmpproject.convention.quality

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

class BkpQualityStylePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.diffplug.spotless")

            extensions.configure<SpotlessExtension> {
                // Spotless cannot auto-detect Android or KMP source layouts, so the target is
                // explicit. `src/**` rather than `src/*/kotlin/**`: `androidApp` keeps its Kotlin
                // under `src/main/java`, which a kotlin-only glob skips without saying so.
                // `**/build` matches the directory, so Gradle *prunes* it while walking.
                // `**/build/**` matches only files inside it, which filters the results but still
                // descends -- and that walk races with whatever is writing to a sibling module's
                // `build/` under `org.gradle.parallel`, failing with "Could not read path".
                val generatedOutput = "**/build"

                kotlin {
                    target("src/**/*.kt")
                    targetExclude(generatedOutput)
                    ktfmt(KTFMT_VERSION).kotlinlangStyle()
                }
                // Build scripts were formatted by nothing before this block.
                kotlinGradle {
                    targetExclude(generatedOutput)
                    ktfmt(KTFMT_VERSION).kotlinlangStyle()
                }
            }

            // The root project has build scripts worth formatting but no sources to analyse.
            // `parent` reaches into another project's model, which project isolation forbids; the
            // root project is the one whose path is ":".
            if (path == ":") return

            apply(plugin = "dev.detekt")
            extensions.configure<DetektExtension> {
                // detekt 2.x's extension is a Property-based interface, not a POJO.
                // `isolated.rootProject` reads the root's directory without touching its mutable
                // `Project` model, which is what project isolation forbids.
                config.setFrom(
                    isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"),
                )
                buildUponDefaultConfig.set(true)
                // Currently a no-op in 2.0.0-alpha.6 -- the release variant tasks are registered
                // either way (verified by swapping this for "debug": the task list is identical).
                // Kept so the intent survives until detekt honours it again.
                ignoredBuildTypes.set(listOf("release"))
            }
            tasks.withType<Detekt>().configureEach {
                exclude {
                    it.file.absolutePath.contains("build/")
                }
            }

            dependencies {
                "detektPlugins"(libs.findLibrary("detekt.composeRules").get())
            }

            val detektAll = tasks.register("detektAll") {
                dependsOn(tasks.withType<Detekt>())
            }

            // `tasks.matching { }` has to realize every task in the project to test the predicate.
            // `check` comes from `lifecycle-base`, which the primary plugin's Kotlin/Android
            // plugins pull in -- possibly after this one -- so the wiring waits for it instead of
            // scanning for it.
            pluginManager.withPlugin("lifecycle-base") {
                tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
                    dependsOn(detektAll)
                }
            }
        }
    }

    private companion object {
        // Pinned deliberately: spotless bundles a default ktfmt, so leaving it implicit means a
        // spotless upgrade reformats the whole repo as a side effect of a version bump.
        const val KTFMT_VERSION = "0.63"
    }
}
