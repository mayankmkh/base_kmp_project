package dev.mayankmkh.basekmpproject.convention.validation

import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType

class BkpValidationGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val root = target.rootProject
        synchronized(root) {
            if (root.extensions.extraProperties.has(VALIDATION_KEY)) return
            root.extensions.extraProperties.set(VALIDATION_KEY, true)
        }

        target.gradle.projectsEvaluated {
            root.allprojects.forEach(::validateProject)
        }
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
        val isDesktopPrimary = primary == "bkp.desktop.app"

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

        if (!isKmpPrimary && extension.features.cocoapods.get()) {
            throw GradleException("${project.path}: cocoapods feature is only supported for bkp.kmp* plugins")
        }
        if (isKmpPrimary && extension.features.cocoapods.get() &&
            !project.pluginManager.hasPlugin("org.jetbrains.kotlin.native.cocoapods")
        ) {
            throw GradleException("${project.path}: cocoapods feature is enabled but Kotlin Cocoapods plugin is not applied")
        }
        if (isKmpPrimary &&
            (!extension.targets.android.get() || !extension.targets.jvm.get() || !extension.targets.ios.get())
        ) {
            throw GradleException(
                "${project.path}: target overrides are reserved for future implementation. Keep android/jvm/ios enabled."
            )
        }

        if (!isKmpPrimary && !isDesktopPrimary &&
            (extension.targets.android.get() || extension.targets.jvm.get() || extension.targets.ios.get())
        ) {
            throw GradleException("${project.path}: targets.* overrides are only valid for bkp.kmp* plugins")
        }
    }

    companion object {
        private const val VALIDATION_KEY = "bkp.validation.graph.registered"
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
