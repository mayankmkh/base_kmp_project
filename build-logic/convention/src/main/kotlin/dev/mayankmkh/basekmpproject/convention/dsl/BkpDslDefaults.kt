package dev.mayankmkh.basekmpproject.convention.dsl

import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

internal fun Project.bkpModuleExtension(): BkpModuleExtension {
    val existing = extensions.findByType<BkpModuleExtension>()
    if (existing != null) return existing

    return extensions.create<BkpModuleExtension>("bkpModule").apply {
        // Default safe/off unless a primary plugin overrides specific defaults.
        targets.android.convention(false)
        targets.jvm.convention(false)
        targets.ios.convention(false)

        features.flavorsDemoProd.convention(false)
        features.firebase.convention(false)
    }
}
