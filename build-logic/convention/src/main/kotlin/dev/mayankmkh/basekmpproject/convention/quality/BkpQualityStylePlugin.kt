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

class BkpQualityStylePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target.parent == null) return
        with(target) {
            apply(plugin = "com.diffplug.spotless")
            apply(plugin = "dev.detekt")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("src/*/kotlin/**/*.kt")
                    targetExclude("build/**/*.kt")
                    ktfmt().kotlinlangStyle()
                }
            }
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

            tasks.matching { it.name == "check" }.configureEach {
                dependsOn(detektAll)
            }
        }
    }
}
