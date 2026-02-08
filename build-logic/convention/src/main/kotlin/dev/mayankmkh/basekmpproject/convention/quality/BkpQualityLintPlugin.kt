package dev.mayankmkh.basekmpproject.convention.quality

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import com.android.build.gradle.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class BkpQualityLintPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") ->
                    configure<ApplicationExtension> { lint(Lint::configureLint) }

                pluginManager.hasPlugin("com.android.library") ->
                    configure<LibraryExtension> { lint(Lint::configureLint) }

                pluginManager.hasPlugin("com.android.test") ->
                    configure<TestExtension> { lint(Lint::configureLint) }

                pluginManager.hasPlugin("com.android.kotlin.multiplatform.library") -> {
                    apply(plugin = "com.android.lint")
                    configure<Lint>(Lint::configureLint)
                }
            }
        }
    }
}

private fun Lint.configureLint() {
    xmlReport = true
    sarifReport = true
    checkDependencies = true
    disable += "GradleDependency"
}
