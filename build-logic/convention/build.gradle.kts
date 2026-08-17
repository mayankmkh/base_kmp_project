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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.android.lint)
}

group = "dev.mayankmkh.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// The convention plugins compile against AGP, KGP and the rest but never ship them -- a consuming
// build brings its own. TestKit builds have no such build: `withPluginClasspath()` injects exactly
// what is listed here, and a synthetic project applying `bkp.kmp.lib` needs KGP, AGP, Spotless,
// detekt and lint on that classpath because the plugin applies all of them.
val testPluginClasspath = configurations.create("testPluginClasspath")

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.firebase.crashlytics.gradlePlugin)
    compileOnly(libs.firebase.performance.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.powerAssert.gradlePlugin)
    compileOnly(libs.jetbrains.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    lintChecks(libs.androidx.lint.gradle)
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())

    testPluginClasspath(libs.android.gradlePlugin)
    testPluginClasspath(libs.kotlin.gradlePlugin)
    testPluginClasspath(libs.kotlin.powerAssert.gradlePlugin)
    testPluginClasspath(libs.spotless.gradlePlugin)
    testPluginClasspath(libs.detekt.gradlePlugin)
    testPluginClasspath(libs.compose.gradlePlugin)
    testPluginClasspath(libs.jetbrains.compose.gradlePlugin)
    testPluginClasspath(libs.gms.gradlePlugin)
    testPluginClasspath(libs.firebase.crashlytics.gradlePlugin)
    testPluginClasspath(libs.firebase.performance.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }

    pluginUnderTestMetadata {
        pluginClasspath.from(testPluginClasspath)
    }

    test {
        useJUnitPlatform()
        // The synthetic projects apply the real version catalog rather than a stub, so a test fails
        // when a plugin asks for a key the project does not actually have.
        systemProperty(
            "bkp.test.versionCatalog",
            isolated.rootProject.projectDirectory.file("../gradle/libs.versions.toml").asFile.absolutePath,
        )
    }
}

gradlePlugin {
    plugins {
        register("bkpAndroidApp") {
            id = "bkp.android.app"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidAppPlugin"
        }
        register("bkpAndroidAppCompose") {
            id = "bkp.android.app.compose"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidAppComposePlugin"
        }
        register("bkpAndroidAppFirebase") {
            id = "bkp.android.app.firebase"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidAppFirebasePlugin"
        }
        register("bkpAndroidLib") {
            id = "bkp.android.lib"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidLibPlugin"
        }
        register("bkpAndroidTest") {
            id = "bkp.android.test"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidTestPlugin"
        }
        register("bkpKmpLib") {
            id = "bkp.kmp.lib"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpLibPlugin"
        }
        register("bkpKmpLibCompose") {
            id = "bkp.kmp.lib.compose"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpLibComposePlugin"
        }
        register("bkpKmpFeature") {
            id = "bkp.kmp.feature"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpFeaturePlugin"
        }
        register("bkpKmpFeatureCompose") {
            id = "bkp.kmp.feature.compose"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpFeatureComposePlugin"
        }
        register("bkpDesktopApp") {
            id = "bkp.desktop.app"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpDesktopAppPlugin"
        }
        register("bkpWebApp") {
            id = "bkp.web.app"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpWebAppPlugin"
        }
        register("bkpQualityStyle") {
            id = "bkp.quality.style"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.quality.BkpQualityStylePlugin"
        }
        register("bkpQualityLint") {
            id = "bkp.quality.lint"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.quality.BkpQualityLintPlugin"
        }
        register("bkpValidationGraph") {
            id = "bkp.validation.graph"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.validation.BkpValidationGraphPlugin"
        }
    }
}
