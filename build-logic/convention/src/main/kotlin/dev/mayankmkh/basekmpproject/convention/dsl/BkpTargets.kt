package dev.mayankmkh.basekmpproject.convention.dsl

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.mayankmkh.basekmpproject.configureKotlinMultiplatformAndroidLibrary
import dev.mayankmkh.basekmpproject.convention.core.registerVerifyFullModule
import dev.mayankmkh.basekmpproject.wasmJsBrowser
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl

/**
 * Per-module target selection, reached as `kotlin { bkpTargets { … } }`.
 *
 * The KMP primaries declare no targets of their own: creating a target is irreversible (KGP has no
 * remove API, and `targets.remove(…)` leaves the tasks behind), so the only way a module can own
 * its platform set is for nothing to be created until the module asks. Every selector creates its
 * targets synchronously -- a nested extension has no callback for "the block finished", so there is
 * no later point at which a collected declaration could be materialised.
 *
 * [default] is the standard platform policy. A deliberately smaller set must go through
 * [exception], so the reason sits beside the selection instead of in a commit message.
 */
abstract class BkpTargets
@Inject
constructor(
    private val project: Project,
    private val kotlin: KotlinMultiplatformExtension,
) {
    private val names = mutableSetOf<String>()
    private val declaredPlatforms = linkedSetOf<BkpPlatform>()
    private var exceptionReason: String? = null
    private var androidDeclared = false
    private var iosDeclared = false

    internal val declaredTargetNames: Set<String>
        get() = names

    /**
     * The platforms the module asked for, as opposed to [declaredTargetNames], which is the target
     * names those platforms happened to produce. The validator compares platforms rather than names
     * because "did this module drop iOS" is not answerable from a name set that legitimately varies
     * with the toolchain.
     */
    internal val selectedPlatforms: Set<BkpPlatform>
        get() = declaredPlatforms

    internal val documentedExceptionReason: String?
        get() = exceptionReason

    fun default() {
        if (exceptionReason != null) {
            throw GradleException(
                "${project.path}: bkpTargets.default() cannot be used inside bkpTargets.exception()."
            )
        }
        android()
        jvm()
        ios()
        web()
    }

    /**
     * Declares a target set smaller than [default], with [reason] recorded in the build script.
     *
     * The guards all exist to stop the reason from drifting away from what it explains: it has to
     * wrap the selectors rather than trail them, it has to say something, and it has to describe an
     * actual exclusion.
     */
    fun exception(reason: String, action: Action<BkpTargets>) {
        if (declaredPlatforms.isNotEmpty()) {
            throw GradleException(
                "${project.path}: bkpTargets.exception() must wrap the target selectors instead of following them."
            )
        }
        if (reason.isBlank()) {
            throw GradleException(
                "${project.path}: bkpTargets.exception() requires a non-empty reason."
            )
        }
        exceptionReason = reason.trim()
        action.execute(this)
        if (declaredPlatforms.isEmpty()) {
            throw GradleException(
                "${project.path}: bkpTargets.exception() must declare at least one target."
            )
        }
        // An exception that keeps every platform is not an exception; it would leave a documented
        // reason in the tree that no longer describes anything.
        if (declaredPlatforms == BKP_DEFAULT_PLATFORMS) {
            throw GradleException(
                "${project.path}: bkpTargets.exception() declares the full default platform set, " +
                    "so it excludes nothing. Use bkpTargets.default() instead."
            )
        }
    }

    fun android() = android {}

    fun android(action: Action<KotlinMultiplatformAndroidLibraryTarget>) {
        declaredPlatforms += BkpPlatform.ANDROID
        if (!androidDeclared) {
            androidDeclared = true
            record {
                // Applying the plugin *is* what creates the Android target, so this cannot happen
                // in the convention plugin without making Android mandatory. Applying it
                // mid-evaluation works, including from inside this `kotlin { }` action; what it
                // costs is the type-safe `kotlin { android { } }` accessor, which Gradle only
                // generates for plugins present in `plugins { }` before the script is compiled.
                // The action parameter above is the replacement for it.
                project.pluginManager.apply("com.android.kotlin.multiplatform.library")
                project.configureKotlinMultiplatformAndroidLibrary(kotlin)
            }
        }
        kotlin.targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach(action)
    }

    fun jvm() {
        declaredPlatforms += BkpPlatform.DESKTOP
        record { kotlin.jvm() }
    }

    fun ios() = ios {}

    fun ios(action: Action<KotlinNativeTarget>) {
        declaredPlatforms += BkpPlatform.IOS
        if (!iosDeclared) {
            iosDeclared = true
            project.registerVerifyFullModule { it == "linkDebugFrameworkIosSimulatorArm64" }
        }
        record {
            action.execute(kotlin.iosArm64())
            action.execute(kotlin.iosSimulatorArm64())
        }
    }

    /**
     * Kotlin/Wasm in a browser. Named for the platform rather than the backend because which
     * backend serves the browser best is not the module's business -- see [wasmJsBrowser].
     *
     * Declaring this is what forces `org.gradle.isolated-projects` off, for reasons recorded in
     * `gradle.properties`.
     */
    fun web() = web {}

    fun web(action: Action<KotlinWasmJsTargetDsl>) {
        declaredPlatforms += BkpPlatform.WEB
        record { kotlin.wasmJsBrowser { action.execute(this) } }
    }

    /**
     * Records whatever [create] added to the target container, so the validator can tell a declared
     * target from one a build script created behind the DSL's back. Names rather than a hardcoded
     * list because the AGP target's name is AGP's to choose.
     */
    private fun record(create: () -> Unit) {
        val before = kotlin.targets.names.toSet()
        create()
        names += kotlin.targets.names - before
    }
}

/** The platforms a module can select, independent of how many KMP targets each one creates. */
enum class BkpPlatform {
    ANDROID,
    IOS,
    DESKTOP,
    WEB,
}

internal val BKP_DEFAULT_PLATFORMS: Set<BkpPlatform> = BkpPlatform.entries.toSet()
