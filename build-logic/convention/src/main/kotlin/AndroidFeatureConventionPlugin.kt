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

import com.android.build.gradle.LibraryExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import kotlin.text.get

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "basekmpproject.android.library")
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<LibraryExtension> {
                testOptions.animationsDisabled = true
            }

            dependencies {
                "implementation"(project(":shared:libs:arch:core"))
                "implementation"(project(":shared:libs:coroutines-x"))
//                "implementation"(project(":shared:libs:crash-reporter:core"))
                "implementation"(project(":shared:libs:networking"))
//                "implementation"(project(":shared:libs:koin"))
//                "implementation"(project(":shared:libs:analytics:main"))
                "implementation"(libs.findLibrary("kotlinx.coroutines.core").get())
                "implementation"(libs.findLibrary("kotlinx.serialization.json").get())
                "implementation"(libs.findLibrary("decompose.decompose").get())
                "implementation"(libs.findLibrary("decompose.extensions.compose").get())
                "implementation"(libs.findLibrary("essenty.lifecycle.coroutines").get())
                "implementation"(project(":shared:libs:designsystem"))

                "androidTestImplementation"(
                    libs.findLibrary("androidx.lifecycle.runtimeTesting").get(),
                )

                val bom = libs.findLibrary("koin.bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("koin.core").get())
            }
        }
    }
}
