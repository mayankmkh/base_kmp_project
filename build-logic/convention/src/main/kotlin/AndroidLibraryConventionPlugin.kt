/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.dsl.androidLibrary
import com.android.build.gradle.LibraryExtension
import dev.mayankmkh.basekmpproject.configureFlavors
import dev.mayankmkh.basekmpproject.configureKotlinAndroid
import dev.mayankmkh.basekmpproject.configureKotlinMultiplatformAndroidLibrary
import dev.mayankmkh.basekmpproject.configurePrintApksTask
import dev.mayankmkh.basekmpproject.disableUnnecessaryAndroidTests
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val isKmpProject = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
            if (isKmpProject) {
                apply(plugin = "com.android.kotlin.multiplatform.library")
            } else {
                apply(plugin = "com.android.library")
                apply(plugin = "org.jetbrains.kotlin.android")
            }
            apply(plugin = "basekmpproject.android.lint")
            apply(plugin = "basekmpproject.style.enforcer")

            if (isKmpProject) {
                val hasHostTests = projectDir.resolve("src/androidHostTest").exists() ||
                    projectDir.resolve("src/test").exists()
                val hasDeviceTests = projectDir.resolve("src/androidDeviceTest").exists() ||
                    projectDir.resolve("src/androidTest").exists()

                extensions.configure<KotlinMultiplatformExtension> {
                    androidLibrary {
                        configureKotlinMultiplatformAndroidLibrary(this)

                        if (hasHostTests) {
                            withHostTest {
                                isIncludeAndroidResources = true
                            }
                        }
                        if (hasDeviceTests) {
                            withDeviceTest {
                                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                                animationsDisabled = true
                            }
                        }
                    }
                }
            } else {
                extensions.configure<LibraryExtension> {
                    configureKotlinAndroid(this)
                    defaultConfig.targetSdk = 36
                    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    testOptions.animationsDisabled = true
                    namespace = "dev.mayankmkh.basekmpproject" + project.path.replace(':', '.').replace('-', '.')
                    configureFlavors(this)
                    // The resource prefix is derived from the module name,
                    // so resources inside ":core:module1" must be prefixed with "core_module1_"
                    resourcePrefix =
                        path.split("""\W""".toRegex()).drop(1).distinct().joinToString(separator = "_")
                            .lowercase() + "_"
                    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
                    sourceSets["main"].res.srcDirs("src/androidMain/res")
                    sourceSets["main"].resources.srcDirs("src/commonMain/resources")
                }
                extensions.configure<LibraryAndroidComponentsExtension> {
                    configurePrintApksTask(this)
                    disableUnnecessaryAndroidTests(target)
                }
            }

            dependencies {
                if (isKmpProject) {
                    if (projectDir.resolve("src/androidHostTest").exists() || projectDir.resolve("src/test").exists()) {
                        "androidHostTestImplementation"(libs.findLibrary("kotlin.test").get())
                    }
                    if (projectDir.resolve("src/androidDeviceTest").exists() || projectDir.resolve("src/androidTest").exists()) {
                        "androidDeviceTestImplementation"(libs.findLibrary("kotlin.test").get())
                    }
                } else {
                    "androidTestImplementation"(libs.findLibrary("kotlin.test").get())
                    "testImplementation"(libs.findLibrary("kotlin.test").get())
                }
            }
        }
    }
}
