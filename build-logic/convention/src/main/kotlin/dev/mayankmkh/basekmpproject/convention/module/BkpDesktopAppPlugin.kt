package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.convention.dsl.bkpModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class BkpDesktopAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "org.jetbrains.compose")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            apply(plugin = "bkp.quality.style")
            apply(plugin = "bkp.validation.graph")

            bkpModuleExtension().apply {
                targets.android.convention(false)
                targets.jvm.convention(true)
                targets.ios.convention(false)
            }

            extensions.configure<KotlinMultiplatformExtension> {
                if (targets.findByName("desktop") == null) {
                    jvm("desktop")
                }
            }
        }
    }
}
