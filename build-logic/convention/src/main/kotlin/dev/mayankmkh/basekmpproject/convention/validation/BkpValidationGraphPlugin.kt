package dev.mayankmkh.basekmpproject.convention.validation

import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import dev.mayankmkh.basekmpproject.convention.dsl.BkpTargets
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class BkpValidationGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Each project validates only itself. Walking `rootProject.allprojects` from
        // `gradle.projectsEvaluated` -- and the extraProperties flag that made that walk happen once
        // -- read and mutated other projects' models, which project isolation forbids. Every module
        // that has a bkp primary plugin also gets this one, so per-project checks cover exactly the
        // same set, and now fail at the offending project rather than after the whole build has
        // configured.
        //
        // `afterEvaluate` is required, not incidental: the values being validated come from the
        // `bkpModule { }` block in the module's own build script, which runs after plugins apply.
        target.afterEvaluate(::validateProject)
    }

    private fun validateProject(project: Project) {
        val primaryPlugins = PRIMARY_PLUGIN_IDS.filter(project.pluginManager::hasPlugin)
        val hasAndroidApp = project.pluginManager.hasPlugin("bkp.android.app") ||
            project.pluginManager.hasPlugin("bkp.android.app.compose")
        val hasAndroidAppFirebase = project.pluginManager.hasPlugin("bkp.android.app.firebase")
        val hasAndroidLib = project.pluginManager.hasPlugin("bkp.android.lib")
        val hasAndroidTest = project.pluginManager.hasPlugin("bkp.android.test")
        val hasKmpFeature = project.pluginManager.hasPlugin("bkp.kmp.feature") ||
            project.pluginManager.hasPlugin("bkp.kmp.feature.compose")
        val hasKmpLibOnly = !hasKmpFeature && (
            project.pluginManager.hasPlugin("bkp.kmp.lib") ||
                project.pluginManager.hasPlugin("bkp.kmp.lib.compose")
            )
        val hasDesktopApp = project.pluginManager.hasPlugin("bkp.desktop.app")

        val activeGroups = listOfNotNull(
            "androidApp".takeIf { hasAndroidApp },
            "androidLib".takeIf { hasAndroidLib },
            "androidTest".takeIf { hasAndroidTest },
            "kmpFeature".takeIf { hasKmpFeature },
            "kmpLib".takeIf { hasKmpLibOnly },
            "desktopApp".takeIf { hasDesktopApp },
        )

        if (activeGroups.size > 1) {
            throw GradleException(
                "${project.path}: expected exactly one bkp primary plugin group, found ${activeGroups.joinToString()}"
            )
        }

        val extension = project.extensions.findByType<BkpModuleExtension>() ?: return
        if (activeGroups.isEmpty()) {
            throw GradleException("${project.path}: bkpModule extension is present but no bkp primary plugin is applied")
        }

        val primary = primaryPlugins.first()
        val isAndroidApp = primary.startsWith("bkp.android.app")
        val isKmpPrimary = primary.startsWith("bkp.kmp")

        if (!isAndroidApp && extension.features.flavorsDemoProd.get()) {
            throw GradleException("${project.path}: flavorsDemoProd is only supported for bkp.android.app* plugins")
        }
        if (!isAndroidApp && extension.features.firebase.get()) {
            throw GradleException("${project.path}: firebase is only supported for bkp.android.app* plugins")
        }
        if (hasAndroidAppFirebase && !isAndroidApp) {
            throw GradleException("${project.path}: bkp.android.app.firebase requires a bkp.android.app* primary plugin")
        }
        if (isAndroidApp && extension.features.firebase.get() && !hasAndroidAppFirebase) {
            throw GradleException(
                "${project.path}: firebase is enabled but bkp.android.app.firebase plugin is not applied"
            )
        }

        if (isKmpPrimary) validateTargets(project)
    }

    /**
     * Checks the module's final target set against what it declared in `kotlin { bkpTargets { } }`.
     *
     * Only the final set is observable. KGP's target factories are configure-or-create, so a build
     * script calling `iosArm64()` on an already-declared target is indistinguishable from the
     * declaration itself and cannot be flagged. What is caught is the case that matters: a target
     * that exists without having been declared.
     */
    private fun validateTargets(project: Project) {
        val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
        val declared = (kotlin as ExtensionAware).extensions.getByType<BkpTargets>().declaredTargetNames

        if (declared.isEmpty()) {
            throw GradleException(
                "${project.path}: no targets declared. Add `kotlin { bkpTargets { default() } }` to the build script."
            )
        }

        // KGP always registers a `metadata` target of its own; it is not a platform and is never
        // declared, so comparing raw target names would reject every module.
        val created = kotlin.targets.filterNot { it.platformType == KotlinPlatformType.common }.map { it.name }
        val undeclared = created - declared
        if (undeclared.isNotEmpty()) {
            throw GradleException(
                "${project.path}: ${undeclared.sorted().joinToString()} created outside " +
                    "`kotlin { bkpTargets { } }`. Declare targets there instead."
            )
        }
    }

    companion object {
        private val PRIMARY_PLUGIN_IDS = setOf(
            "bkp.android.app",
            "bkp.android.app.compose",
            "bkp.android.lib",
            "bkp.android.test",
            "bkp.kmp.lib",
            "bkp.kmp.lib.compose",
            "bkp.kmp.feature",
            "bkp.kmp.feature.compose",
            "bkp.desktop.app",
        )
    }
}
