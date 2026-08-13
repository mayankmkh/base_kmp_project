/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mayankmkh.basekmpproject

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Configure Compose-specific options
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension
) {
    // AGP 9's `CommonExtension` is no longer generic and only exposes getters — the block DSL
    // lives on the concrete extension types, so configure the nested objects via property access.
    commonExtension.buildFeatures.compose = true

    // For Robolectric
    commonExtension.testOptions.unitTests.isIncludeAndroidResources = true

    dependencies {
        "implementation"(libs.findBundle("compose.android.main").get())
        "debugImplementation"(libs.findLibrary("compose.ui.tooling").get())
    }

    configureComposeCompiler()
}

internal fun Project.configureKMPCompose(
) {

    dependencies {
        "commonMainImplementation"(libs.findBundle("compose.common.main").get())
    }

    configureComposeCompiler()
}

@Suppress("UnstableApiUsage")
private fun Project.configureComposeCompiler() {
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        fun Provider<String>.onlyIfTrue() = flatMap { provider { it.takeIf(String::toBoolean) } }
        fun Provider<*>.relativeToRootProject(dir: String) = map {
            isolated.rootProject.projectDirectory
                .dir("build")
                .dir(projectDir.toRelativeString(rootDir))
        }.map { it.dir(dir) }

        project.providers.gradleProperty("enableComposeCompilerMetrics").onlyIfTrue()
            .relativeToRootProject("compose-metrics")
            .let(metricsDestination::set)

        project.providers.gradleProperty("enableComposeCompilerReports").onlyIfTrue()
            .relativeToRootProject("compose-reports")
            .let(reportsDestination::set)

        // Only wire the stability config when it actually exists -- adding it unconditionally makes
        // the compiler warn "Stability configuration file not found" on every Compose compilation.
        val stabilityConfig =
            isolated.rootProject.projectDirectory.file("compose_compiler_config.conf")
        if (stabilityConfig.asFile.exists()) {
            stabilityConfigurationFiles.add(stabilityConfig)
        }
    }
}
