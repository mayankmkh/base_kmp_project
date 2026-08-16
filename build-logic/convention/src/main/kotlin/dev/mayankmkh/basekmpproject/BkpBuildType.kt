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

package dev.mayankmkh.basekmpproject

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * The application id suffix each build type carries, so debug and release installs can sit side by
 * side on one device. Paired with [BkpFlavor], which suffixes the same id along the flavor axis.
 */
enum class BkpBuildType(val applicationIdSuffix: String? = null) {
    DEBUG(".debug"),
    RELEASE,
}

/**
 * Applies the build-type convention every `bkp.android.app*` module shares.
 *
 * These are defaults, not decrees. A module's own `android { }` block runs after the plugin, and
 * every value set here is a plain property, so an app that wants an unminified release or a
 * different suffix just says so and wins -- see `BkpAndroidAppPluginTest`.
 *
 * `proguardFiles` is the exception: it appends rather than assigns, and a module cannot take an
 * entry back out without `setProguardFiles`, which would also discard AGP's default. So the
 * module's own rules file is added only when it exists. Listing it unconditionally would make it
 * mandatory -- AGP 9 fails the build on a missing ProGuard file
 * (`android.proguard.failOnMissingFiles` defaults to true) -- and a convention should not oblige
 * every app module to carry an empty file.
 */
internal fun Project.configureBuildTypes(applicationExtension: ApplicationExtension) {
    applicationExtension.apply {
        // The container is only invariant through the block form; the `buildTypes` getter is
        // out-projected and cannot be configured. Same shape as `configureFlavors`.
        buildTypes {
            named("debug") {
                applicationIdSuffix = BkpBuildType.DEBUG.applicationIdSuffix
            }
            named("release") {
                isMinifyEnabled = true
                applicationIdSuffix = BkpBuildType.RELEASE.applicationIdSuffix
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                if (file(MODULE_PROGUARD_RULES).exists()) {
                    proguardFiles(MODULE_PROGUARD_RULES)
                }
            }
        }
    }
}

private const val MODULE_PROGUARD_RULES = "proguard-rules.pro"
