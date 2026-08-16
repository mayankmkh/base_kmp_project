package dev.mayankmkh.basekmpproject.convention.dsl

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.mayankmkh.basekmpproject.configureKotlinMultiplatformAndroidLibrary
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import javax.inject.Inject

/**
 * Per-module target selection, reached as `kotlin { bkpTargets { … } }`.
 *
 * The KMP primaries declare no targets of their own: creating a target is irreversible (KGP has no
 * remove API, and `targets.remove(…)` leaves the tasks behind), so the only way a module can own its
 * platform set is for nothing to be created until the module asks. Every selector creates its
 * targets synchronously — a nested extension has no callback for "the block finished", so there is
 * no later point at which a collected declaration could be materialised.
 */
abstract class BkpTargets @Inject constructor(
    private val project: Project,
    private val kotlin: KotlinMultiplatformExtension,
) {
    private val names = mutableSetOf<String>()
    private var androidDeclared = false

    internal val declaredTargetNames: Set<String> get() = names

    fun default() {
        android()
        jvm()
        ios()
    }

    fun android() = android {}

    fun android(action: Action<KotlinMultiplatformAndroidLibraryTarget>) {
        if (!androidDeclared) {
            androidDeclared = true
            record {
                // Applying the plugin *is* what creates the Android target, so this cannot happen in
                // the convention plugin without making Android mandatory. Applying it mid-evaluation
                // works, including from inside this `kotlin { }` action; what it costs is the
                // type-safe `kotlin { android { } }` accessor, which Gradle only generates for
                // plugins present in `plugins { }` before the script is compiled. The action
                // parameter above is the replacement for it.
                project.pluginManager.apply("com.android.kotlin.multiplatform.library")
                project.configureKotlinMultiplatformAndroidLibrary(kotlin)
            }
        }
        kotlin.targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach(action)
    }

    fun jvm() {
        record { kotlin.jvm() }
    }

    fun ios() = ios {}

    fun ios(action: Action<KotlinNativeTarget>) {
        record {
            action.execute(kotlin.iosArm64())
            action.execute(kotlin.iosSimulatorArm64())
        }
    }

    fun web(): Nothing = throw GradleException(
        "bkpTargets.web() is not implemented: KGP's WasmNpmResolverPlugin applies " +
            "WasmNodeJsRootPlugin to the root project, which project isolation forbids."
    )

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
