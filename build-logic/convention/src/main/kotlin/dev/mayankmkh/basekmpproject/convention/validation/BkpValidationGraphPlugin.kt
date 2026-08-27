package dev.mayankmkh.basekmpproject.convention.validation

import dev.mayankmkh.basekmpproject.convention.dsl.BKP_DEFAULT_PLATFORMS
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
        // `gradle.projectsEvaluated` -- and the extraProperties flag that made that walk happen
        // once
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
        val hasAndroidApp = project.hasAnyPlugin(BKP_ANDROID_APP, BKP_ANDROID_APP_COMPOSE)
        val hasAndroidAppFirebase = project.pluginManager.hasPlugin(BKP_ANDROID_APP_FIREBASE)
        val hasAndroidLib = project.pluginManager.hasPlugin(BKP_ANDROID_LIB)
        val hasAndroidTest = project.pluginManager.hasPlugin(BKP_ANDROID_TEST)
        val hasKmpFeature = project.hasAnyPlugin(BKP_KMP_FEATURE, BKP_KMP_FEATURE_COMPOSE)
        val hasWebApp = project.pluginManager.hasPlugin(BKP_WEB_APP)
        // `bkp.kmp.feature*` and `bkp.web.app` are both built on `bkp.kmp.lib*`, so the lib group
        // is only the module's own primary when neither of them claimed it first.
        val hasKmpLibOnly =
            !hasKmpFeature && !hasWebApp && project.hasAnyPlugin(BKP_KMP_LIB, BKP_KMP_LIB_COMPOSE)
        val hasDesktopApp = project.pluginManager.hasPlugin(BKP_DESKTOP_APP)

        val activeGroups =
            listOfNotNull(
                "androidApp".takeIf { hasAndroidApp },
                "androidLib".takeIf { hasAndroidLib },
                "androidTest".takeIf { hasAndroidTest },
                "kmpFeature".takeIf { hasKmpFeature },
                "kmpLib".takeIf { hasKmpLibOnly },
                "desktopApp".takeIf { hasDesktopApp },
                "webApp".takeIf { hasWebApp },
            )

        if (activeGroups.size > 1) {
            throw GradleException(
                "${project.path}: expected exactly one bkp primary plugin group, found ${activeGroups.joinToString()}"
            )
        }

        // Ahead of the extension lookup on purpose: the firebase plugin creates no `bkpModule`, so
        // a module that applies it and nothing else has no extension and would return early.
        if (hasAndroidAppFirebase && !hasAndroidApp) {
            throw GradleException(
                "${project.path}: bkp.android.app.firebase requires a bkp.android.app* primary plugin"
            )
        }

        val extension = project.extensions.findByType<BkpModuleExtension>() ?: return
        if (activeGroups.isEmpty()) {
            throw GradleException(
                "${project.path}: bkpModule extension is present but no bkp primary plugin is applied"
            )
        }

        val primary = primaryPlugins.first()
        val isAndroidApp = primary.startsWith(BKP_ANDROID_APP)
        val isKmpPrimary = primary.startsWith("bkp.kmp")

        if (!isAndroidApp && extension.features.demoProdFlavorsEnabled) {
            throw GradleException(
                "${project.path}: demoProdFlavors() is only supported for bkp.android.app* plugins"
            )
        }

        // A feature module that wants Compose has its own primary; reaching for the library's
        // Compose variant instead leaves the feature conventions applied without them.
        if (
            project.pluginManager.hasPlugin(BKP_KMP_FEATURE) &&
                project.pluginManager.hasPlugin(BKP_KMP_LIB_COMPOSE) &&
                !project.pluginManager.hasPlugin(BKP_KMP_FEATURE_COMPOSE) &&
                !hasWebApp
        ) {
            throw GradleException(
                "${project.path}: incompatible bkp convention plugins: " +
                    "$BKP_KMP_FEATURE, $BKP_KMP_LIB_COMPOSE. Use $BKP_KMP_FEATURE_COMPOSE."
            )
        }

        validateComposeCombination(project, hasAndroidApp, hasKmpFeature, hasKmpLibOnly, hasWebApp)

        // `bkp.web.app` declares the wasm target itself and is deliberately single-platform, so it
        // is exempt from the default-or-exception rule while still being checked for targets
        // created behind the DSL's back.
        if (isKmpPrimary) validateTargets(project, requireDefaultOrException = !hasWebApp)
    }

    /**
     * Rejects the raw Compose plugins sitting next to a non-Compose bkp primary.
     *
     * That combination compiles, which is the problem: the module gets the Compose compiler without
     * the dependencies and settings the matching bkp Compose convention would have brought, and the
     * difference only shows up later as a missing artifact or a version skew. Modules whose Compose
     * arrived *through* a bkp Compose convention are the same plugins applied for the right reason,
     * so they fall through to the `else` branch untouched.
     */
    private fun validateComposeCombination(
        project: Project,
        hasAndroidApp: Boolean,
        hasKmpFeature: Boolean,
        hasKmpLibOnly: Boolean,
        hasWebApp: Boolean,
    ) {
        val composeApplied =
            project.hasAnyPlugin("org.jetbrains.compose", "org.jetbrains.kotlin.plugin.compose")
        if (!composeApplied || hasWebApp) return

        val hasAndroidAppCompose = project.pluginManager.hasPlugin(BKP_ANDROID_APP_COMPOSE)
        val hasFeatureCompose = project.pluginManager.hasPlugin(BKP_KMP_FEATURE_COMPOSE)
        val hasLibCompose = project.pluginManager.hasPlugin(BKP_KMP_LIB_COMPOSE)
        val replacement =
            when {
                hasAndroidApp && !hasAndroidAppCompose -> BKP_ANDROID_APP_COMPOSE
                hasKmpFeature && !hasFeatureCompose -> BKP_KMP_FEATURE_COMPOSE
                hasKmpLibOnly && !hasLibCompose -> BKP_KMP_LIB_COMPOSE
                else -> return
            }
        throw GradleException(
            "${project.path}: raw Compose plugins were combined with a non-Compose bkp convention. " +
                "Use $replacement so Compose compiler and dependencies stay consistent."
        )
    }

    /**
     * Checks the module's final target set against what it declared in `kotlin { bkpTargets { } }`.
     *
     * Only the final set is observable. KGP's target factories are configure-or-create, so a build
     * script calling `iosArm64()` on an already-declared target is indistinguishable from the
     * declaration itself and cannot be flagged. What is caught is the case that matters: a target
     * that exists without having been declared.
     */
    private fun validateTargets(project: Project, requireDefaultOrException: Boolean) {
        val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
        val bkpTargets = (kotlin as ExtensionAware).extensions.getByType<BkpTargets>()

        if (bkpTargets.declaredTargetNames.isEmpty()) {
            throw GradleException(
                "${project.path}: no targets declared. Add `kotlin { bkpTargets { default() } }` to the build script."
            )
        }

        if (
            requireDefaultOrException &&
                bkpTargets.selectedPlatforms != BKP_DEFAULT_PLATFORMS &&
                bkpTargets.documentedExceptionReason == null
        ) {
            throw GradleException(
                "${project.path}: a non-default bkp target set requires " +
                    "`bkpTargets { exception(\"reason\") { ... } }`."
            )
        }

        // KGP always registers a `metadata` target of its own; it is not a platform and is never
        // declared, so comparing raw target names would reject every module.
        val created =
            kotlin.targets
                .filterNot { it.platformType == KotlinPlatformType.common }
                .map { it.name }
        val undeclared = created - bkpTargets.declaredTargetNames
        if (undeclared.isNotEmpty()) {
            throw GradleException(
                "${project.path}: ${undeclared.sorted().joinToString()} created outside " +
                    "`kotlin { bkpTargets { } }`. Declare targets there instead."
            )
        }
    }

    private fun Project.hasAnyPlugin(vararg ids: String): Boolean =
        ids.any(pluginManager::hasPlugin)

    companion object {
        private const val BKP_ANDROID_APP = "bkp.android.app"
        private const val BKP_ANDROID_APP_COMPOSE = "bkp.android.app.compose"
        private const val BKP_ANDROID_APP_FIREBASE = "bkp.android.app.firebase"
        private const val BKP_ANDROID_LIB = "bkp.android.lib"
        private const val BKP_ANDROID_TEST = "bkp.android.test"
        private const val BKP_DESKTOP_APP = "bkp.desktop.app"
        private const val BKP_WEB_APP = "bkp.web.app"
        private const val BKP_KMP_LIB = "bkp.kmp.lib"
        private const val BKP_KMP_LIB_COMPOSE = "bkp.kmp.lib.compose"
        private const val BKP_KMP_FEATURE = "bkp.kmp.feature"
        private const val BKP_KMP_FEATURE_COMPOSE = "bkp.kmp.feature.compose"

        private val PRIMARY_PLUGIN_IDS =
            setOf(
                BKP_ANDROID_APP,
                BKP_ANDROID_APP_COMPOSE,
                BKP_ANDROID_LIB,
                BKP_ANDROID_TEST,
                BKP_KMP_LIB,
                BKP_KMP_LIB_COMPOSE,
                BKP_KMP_FEATURE,
                BKP_KMP_FEATURE_COMPOSE,
                BKP_DESKTOP_APP,
                BKP_WEB_APP,
            )
    }
}
