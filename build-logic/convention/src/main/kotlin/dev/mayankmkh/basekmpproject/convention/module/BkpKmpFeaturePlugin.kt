package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.convention.core.addKmpFeatureBundle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class BkpKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "bkp.kmp.lib")
            addKmpFeatureBundle()
        }
    }
}
