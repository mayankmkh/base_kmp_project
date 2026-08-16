package dev.mayankmkh.basekmpproject.convention.quality

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class BkpQualityLintPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Reactive rather than a one-shot `hasPlugin` check: a KMP module applies the AGP KMP
            // plugin from its own `bkpTargets { }` block, long after this plugin has applied. AGP
            // forbids combining these plugins, so at most one branch ever fires.
            pluginManager.withPlugin("com.android.application") {
                configure<ApplicationExtension> { lint(Lint::configureLint) }
            }
            pluginManager.withPlugin("com.android.library") {
                configure<LibraryExtension> { lint(Lint::configureLint) }
            }
            pluginManager.withPlugin("com.android.test") {
                configure<TestExtension> { lint(Lint::configureLint) }
            }
            pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
                apply(plugin = "com.android.lint")
                configure<Lint>(Lint::configureLint)
            }
        }
    }
}

private fun Lint.configureLint() {
    // `xmlReport`/`sarifReport` are gone in AGP 9 — lint reports are now always generated.
    checkDependencies = true
    disable += "GradleDependency"
}
