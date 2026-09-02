package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKMPCompose
import dev.mayankmkh.basekmpproject.convention.core.addFeatureRoleDependencies
import dev.mayankmkh.basekmpproject.convention.helix.HelixRole
import dev.mayankmkh.basekmpproject.convention.helix.recordHelixRole
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class BkpKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "bkp.kmp.lib")
            recordHelixRole(HelixRole.FEATURE)
            apply(plugin = "org.jetbrains.compose")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            configureKMPCompose()
            addFeatureRoleDependencies()
        }
    }
}
