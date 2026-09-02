package dev.mayankmkh.basekmpproject.convention.helix

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/** Stable role names shared by role plugins, dependency policy, reports, and Helix tooling. */
enum class HelixRole(val policyName: String) {
    APP("app"),
    FEATURE("feature"),
    UI("ui"),
    CAPABILITY_API("capability_api"),
    CAPABILITY_IMPL("capability_impl"),
    FOUNDATION_API("foundation_api"),
    FOUNDATION_RUNTIME("foundation_runtime"),
    PLATFORM("platform"),
    PLATFORM_API("platform_api"),
    PLATFORM_IMPL("platform_impl"),
    STORAGE("storage"),
    TESTKIT("testkit"),
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
