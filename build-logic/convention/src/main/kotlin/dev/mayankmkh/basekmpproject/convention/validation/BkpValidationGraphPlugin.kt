package dev.mayankmkh.basekmpproject.convention.validation

import dev.mayankmkh.basekmpproject.convention.dsl.BKP_DEFAULT_PLATFORMS
import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import dev.mayankmkh.basekmpproject.convention.dsl.BkpTargets
import dev.mayankmkh.basekmpproject.convention.helix.HelixModuleExtension
import java.time.LocalDate
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class BkpValidationGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target.path == ":") registerRootTasks(target)
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

    private fun registerRootTasks(project: Project) {
        project.pluginManager.apply("base")
        val dependencyPolicy =
            project.layout.projectDirectory.file("config/helix/dependency-policy.json")
        val exceptionRegistry = project.layout.projectDirectory.file("config/helix/exceptions.json")
        val sourceOfTruth =
            project.layout.projectDirectory.file("docs/architecture/helix-kmp-source-of-truth.md")
        val graph =
            project.tasks.register<CheckModuleGraphTask>("checkModuleGraph") {
                group = "verification"
                description = "Validates the Helix role and main-source project dependency graph."
                policyFile.set(dependencyPolicy)
                exceptionsFile.set(exceptionRegistry)
                currentDate.set(LocalDate.now().toString())
                reportFile.set(
                    project.layout.buildDirectory.file("reports/helix/module-graph.json")
                )
            }
        val policySync =
            project.tasks.register<CheckHelixPolicySyncTask>("checkHelixPolicySync") {
                group = "verification"
                description = "Checks the derived Helix dependency policy against its master."
                policyFile.set(dependencyPolicy)
                sourceOfTruthFile.set(sourceOfTruth)
                markerFile.set(project.layout.buildDirectory.file("reports/helix/policy-sync.txt"))
            }
        project.tasks.named("check") {
            dependsOn(graph, policySync)
        }

        // The task actions receive only encoded strings and file inputs. Cross-project Gradle
        // models are inspected once, after every module has declared source sets/dependencies, and
        // are never captured by a task action or configuration-cache entry.
        project.gradle.projectsEvaluated {
            // Gradle creates structural parent projects for paths such as `:feature:posts` even
            // when `feature/` has no build script. They are containers, not Helix modules.
            val projects =
                project.allprojects
                    .filter {
                        it.path != ":" &&
                            (it.projectDir.resolve("build.gradle.kts").isFile ||
                                it.projectDir.resolve("build.gradle").isFile)
                    }
                    .sortedBy { it.path }
            graph.configure {
                nodeRecords.set(
                    projects.map { candidate ->
                        val roles =
                            candidate.extensions
                                .findByType<HelixModuleExtension>()
                                ?.roles
                                ?.orNull
                                .orEmpty()
                                .map { it.policyName }
                        encodeNode(candidate.path, roles, candidate.projectDir.absolutePath)
                    }
                )
                edgeRecords.set(
                    projects
                        .flatMap { candidate ->
                            candidate.configurations
                                .filter { it.name.isMainProjectDependencyConfiguration() }
                                .flatMap { configuration ->
                                    configuration.dependencies
                                        .withType(ProjectDependency::class.java)
                                        .map { dependency ->
                                            encodeEdge(candidate.path, dependency.path)
                                        }
                                }
                        }
                        .distinct()
                        .sorted()
                )
                // Directories, deliberately: the task filters them to `*Main/kotlin/**/*.kt` while
                // it runs. Handing over a resolved file tree instead would freeze the file list
                // into the configuration-cache entry, and a declaration added to a new file would
                // then go unseen by the source rules until something else forced reconfiguration.
                sourceDirectories.from(
                    projects.map { candidate -> candidate.projectDir.resolve("src") }
                )
            }
        }
    }

    private fun String.isMainProjectDependencyConfiguration(): Boolean {
        val lower = lowercase()
        if ("test" in lower) return false
        return this == "api" ||
            this == "implementation" ||
            ((endsWith("Api") || endsWith("Implementation")) && "main" in lower)
    }

    private fun validateProject(project: Project) {
        val primaryPlugins = PRIMARY_PLUGIN_IDS.filter(project.pluginManager::hasPlugin)
        val hasAndroidApp = project.hasAnyPlugin(BKP_ANDROID_APP, BKP_ANDROID_APP_COMPOSE)
        val hasAndroidAppFirebase = project.pluginManager.hasPlugin(BKP_ANDROID_APP_FIREBASE)
        val hasAndroidLib = project.pluginManager.hasPlugin(BKP_ANDROID_LIB)
        val hasAndroidTest = project.pluginManager.hasPlugin(BKP_ANDROID_TEST)
        val hasKmpFeature = project.pluginManager.hasPlugin(BKP_KMP_FEATURE)
        val hasHelixKmpRole = HELIX_KMP_ROLE_PLUGIN_IDS.any(project.pluginManager::hasPlugin)
        val hasWebApp = project.pluginManager.hasPlugin(BKP_WEB_APP)
        // `bkp.kmp.feature*` and `bkp.web.app` are both built on `bkp.kmp.lib*`, so the lib group
        // is only the module's own primary when neither of them claimed it first.
        val hasKmpLibOnly =
            !hasHelixKmpRole &&
                !hasKmpFeature &&
                !hasWebApp &&
                project.pluginManager.hasPlugin(BKP_KMP_LIB)
        val hasDesktopApp = project.pluginManager.hasPlugin(BKP_DESKTOP_APP)

        val activeGroups =
            listOfNotNull(
                "androidApp".takeIf { hasAndroidApp },
                "androidLib".takeIf { hasAndroidLib },
                "androidTest".takeIf { hasAndroidTest },
                "helixKmpRole".takeIf { hasHelixKmpRole },
                "kmpFeature".takeIf { hasKmpFeature && !hasHelixKmpRole },
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
        val isKmpPrimary = hasHelixKmpRole || primary.startsWith("bkp.kmp")

        if (!isAndroidApp && extension.features.demoProdFlavorsEnabled) {
            throw GradleException(
                "${project.path}: demoProdFlavors() is only supported for bkp.android.app* plugins"
            )
        }

        validateComposeCombination(project, hasAndroidApp, hasKmpLibOnly, hasWebApp)

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
        hasKmpLibOnly: Boolean,
        hasWebApp: Boolean,
    ) {
        val composeApplied =
            project.hasAnyPlugin("org.jetbrains.compose", "org.jetbrains.kotlin.plugin.compose")
        if (!composeApplied || hasWebApp) return

        // Helix role plugins own their Compose decision directly. The remaining check catches a
        // raw Compose compiler bolted onto the internal KMP base instead of selecting a role.
        if (
            listOf(BKP_KMP_APP, BKP_KMP_FEATURE, BKP_KMP_UI, BKP_KMP_FOUNDATION_API)
                .any(project.pluginManager::hasPlugin)
        ) {
            return
        }

        val hasAndroidAppCompose = project.pluginManager.hasPlugin(BKP_ANDROID_APP_COMPOSE)
        val replacement =
            when {
                hasAndroidApp && !hasAndroidAppCompose -> BKP_ANDROID_APP_COMPOSE
                hasKmpLibOnly -> "a Compose-owning Helix role plugin"
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
        private const val BKP_KMP_FEATURE = "bkp.kmp.feature"
        private const val BKP_KMP_APP = "bkp.kmp.app"
        private const val BKP_KMP_UI = "bkp.kmp.ui"
        private const val BKP_KMP_CAPABILITY_API = "bkp.kmp.capability.api"
        private const val BKP_KMP_CAPABILITY_IMPL = "bkp.kmp.capability.impl"
        private const val BKP_KMP_FOUNDATION_API = "bkp.kmp.foundation.api"
        private const val BKP_KMP_FOUNDATION_RUNTIME = "bkp.kmp.foundation.runtime"
        private const val BKP_KMP_PLATFORM = "bkp.kmp.platform"
        private const val BKP_KMP_PLATFORM_API = "bkp.kmp.platform.api"
        private const val BKP_KMP_PLATFORM_IMPL = "bkp.kmp.platform.impl"
        private const val BKP_KMP_STORAGE = "bkp.kmp.storage"
        private const val BKP_KMP_TESTKIT = "bkp.kmp.testkit"

        private val HELIX_KMP_ROLE_PLUGIN_IDS =
            setOf(
                BKP_KMP_APP,
                BKP_KMP_FEATURE,
                BKP_KMP_UI,
                BKP_KMP_CAPABILITY_API,
                BKP_KMP_CAPABILITY_IMPL,
                BKP_KMP_FOUNDATION_API,
                BKP_KMP_FOUNDATION_RUNTIME,
                BKP_KMP_PLATFORM,
                BKP_KMP_PLATFORM_API,
                BKP_KMP_PLATFORM_IMPL,
                BKP_KMP_STORAGE,
                BKP_KMP_TESTKIT,
            )

        private val PRIMARY_PLUGIN_IDS =
            setOf(
                BKP_ANDROID_APP,
                BKP_ANDROID_APP_COMPOSE,
                BKP_ANDROID_LIB,
                BKP_ANDROID_TEST,
                BKP_KMP_LIB,
                BKP_KMP_FEATURE,
                BKP_KMP_APP,
                BKP_KMP_UI,
                BKP_KMP_CAPABILITY_API,
                BKP_KMP_CAPABILITY_IMPL,
                BKP_KMP_FOUNDATION_API,
                BKP_KMP_FOUNDATION_RUNTIME,
                BKP_KMP_PLATFORM,
                BKP_KMP_PLATFORM_API,
                BKP_KMP_PLATFORM_IMPL,
                BKP_KMP_STORAGE,
                BKP_KMP_TESTKIT,
                BKP_DESKTOP_APP,
                BKP_WEB_APP,
            )
    }
}
