package dev.mayankmkh.basekmpproject.convention.helix

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/** Stable role facts shared by role plugins, dependency policy, reports, and Helix tooling. */
enum class HelixRole(
    val policyName: String,
    val pluginId: String,
    val pathPattern: Regex,
) {
    APP("app", "bkp.kmp.app", Regex("^:app:[^:]+$")),
    FEATURE("feature", "bkp.kmp.feature", Regex("^:feature:[^:]+$")),
    UI("ui", "bkp.kmp.ui", Regex("^:ui:[^:]+$")),
    CAPABILITY_API(
        "capability_api",
        "bkp.kmp.capability.api",
        Regex("^:capability:[^:]+-api$"),
    ),
    CAPABILITY_IMPL(
        "capability_impl",
        "bkp.kmp.capability.impl",
        Regex("^:capability:[^:]+-impl$"),
    ),
    FOUNDATION_API(
        "foundation_api",
        "bkp.kmp.foundation.api",
        Regex("^:foundation:[^:]+$"),
    ),
    FOUNDATION_RUNTIME(
        "foundation_runtime",
        "bkp.kmp.foundation.runtime",
        Regex("^:foundation:[^:]+$"),
    ),
    PLATFORM(
        "platform",
        "bkp.kmp.platform",
        Regex("^:platform:(?!.*-(?:api|impl)$)[^:]+$"),
    ),
    PLATFORM_API("platform_api", "bkp.kmp.platform.api", Regex("^:platform:[^:]+-api$")),
    PLATFORM_IMPL("platform_impl", "bkp.kmp.platform.impl", Regex("^:platform:[^:]+-impl$")),
    STORAGE("storage", "bkp.kmp.storage", Regex("^:storage:[^:]+$")),
    TESTKIT("testkit", "bkp.kmp.testkit", Regex("^:testkit:[^:]+$")),
}

/**
 * Build-model hand-off from a convention plugin to the root graph validator.
 *
 * This deliberately stores a list instead of a single value: applying two role plugins must remain
 * observable so `MOD-ROLE-MULTIPLE` can explain the mistake rather than letting the last plugin
 * win.
 */
abstract class HelixModuleExtension @Inject constructor() {
    abstract val roles: ListProperty<HelixRole>
}

internal fun Project.recordHelixRole(role: HelixRole) {
    val metadata =
        extensions.findByType<HelixModuleExtension>()
            ?: extensions.create<HelixModuleExtension>("helixModule")
    metadata.roles.add(role)
}
