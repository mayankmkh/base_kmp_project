package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKMPCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class BkpKmpFeatureComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "bkp.kmp.feature")
            apply(plugin = "org.jetbrains.compose")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            configureKMPCompose()
        }
    }
}
