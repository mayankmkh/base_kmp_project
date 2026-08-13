package dev.mayankmkh.basekmpproject.convention.quality

import com.android.build.api.dsl.CommonExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.mayankmkh.basekmpproject.libs
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
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
            apply(plugin = "io.gitlab.arturbosch.detekt")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("src/*/kotlin/**/*.kt")
                    targetExclude("build/**/*.kt")
                    ktfmt().kotlinlangStyle()
                }
            }
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                ignoredBuildTypes = listOf("release")
            }
            // detekt 1.x creates its per-variant Android tasks through AGP's legacy variant API,
            // which AGP 9 removed. On pure-Android modules only the default `detekt` task is left,
            // and it would only see detekt's own `src/{main,test}/{java,kotlin}` defaults — so feed
            // it AGP's source sets instead. Drop this once detekt 2.x (AGP 9 aware) is available.
            pluginManager.withPlugin("com.android.base") {
                val androidSourceSets = (extensions.getByName("android") as CommonExtension).sourceSets
                extensions.configure<DetektExtension> {
                    source.setFrom(
                        androidSourceSets.flatMap { it.java.directories + it.kotlin.directories },
                    )
                }
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
