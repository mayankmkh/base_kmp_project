package dev.mayankmkh.basekmpproject.convention.core

import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// The shipped default only rewrites `kotlin.assert`, which nothing here calls, so the plugin would
// be inert without naming the `kotlin.test` functions in use.
internal val POWER_ASSERT_FUNCTIONS =
    listOf(
        "kotlin.assert",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotEquals",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertNull",
        "kotlin.test.assertNotNull",
    )

/**
 * Attaches power-assert by hand for modules on AGP 9's built-in Kotlin.
 *
 * `org.jetbrains.kotlin.plugin.power-assert` applies cleanly there but does nothing: it hooks
 * `KotlinCompilation`s, and built-in Kotlin creates none, so its `isApplicable` check is never
 * reached. AGP's only subplugin bridge is a kapt special case. The compile task it registers is
 * nonetheless KGP's own `KotlinCompile`, so the compiler plugin can be put on `pluginClasspath`
 * directly -- an unsupported hook that may need revisiting on an AGP upgrade.
 */
internal fun Project.configureAndroidPowerAssert() {
    val compilerPlugin =
        configurations.detachedConfiguration(
            dependencies.create(libs.findLibrary("kotlin.powerAssert.compilerPlugin").get().get())
        )
    val options = POWER_ASSERT_FUNCTIONS.flatMap {
        listOf("-P", "plugin:org.jetbrains.kotlin.powerassert:function=$it")
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        if (name.endsWith("UnitTestKotlin") || name.endsWith("AndroidTestKotlin")) {
            pluginClasspath.from(compilerPlugin)
            compilerOptions.freeCompilerArgs.addAll(options)
        }
    }

    // Without the runtime the rewritten call sites still render a diagram, but every value comes
    // out unquoted, so `"alpha"` and `alpha` read alike.
    dependencies {
        "testImplementation"(libs.findLibrary("kotlin.powerAssert.runtime").get())
        "androidTestImplementation"(libs.findLibrary("kotlin.powerAssert.runtime").get())
    }
}
