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

import com.diffplug.gradle.spotless.SpotlessCheck
import com.diffplug.gradle.spotless.SpotlessTask
import com.diffplug.spotless.LineEnding
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.android.lint)
    alias(libs.plugins.spotless)
}

group = "dev.mayankmkh.buildlogic"

// This project defines `bkp.quality.style`, so it cannot apply it: the plugin does not exist
// until this project is built. Spotless is configured by hand instead, with the same formatter,
// the same targets and the same ktfmt version from the catalog, so the convention plugin sources
// are held to the rules they impose on every other module. The two configurations have to be kept
// in agreement -- change one, change the other, or they fight over the same files.
//
// detekt is deliberately not duplicated here: `androidx.lint:lint-gradle` (applied via
// `lintChecks` below) covers the plugin-authoring failure modes that matter for this code -- eager
// task realization, `Project` captured into task actions, configuration-cache hazards -- and it
// already runs as part of `check`.
spotless {
    lineEndings = LineEnding.UNIX

    val ktfmtVersion = libs.versions.ktfmt.get()
    val generatedOutput = "**/build"

    kotlin {
        target("src/**/*.kt")
        targetExclude(generatedOutput)
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
    kotlinGradle {
        targetExclude(generatedOutput)
        ktfmt(ktfmtVersion).kotlinlangStyle()
    }
}

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

    // Spotless stages formatted copies of every source file under `build/spotless-clean` and
    // removes them once its own task finishes. Lint's JVM analysis walks the build directory, so
    // `check` running the two in parallel makes lint fail with `FileNotFoundException` on a file
    // Spotless has just cleaned up. They are independent otherwise, so ordering is the whole fix.
    // The lint plugin registers these tasks from the variant API, so they do not exist yet by name
    // at script-evaluation time -- hence the lazy match rather than `named(...)`.
    configureEach {
        if (name.startsWith("lintAnalyze")) {
            mustRunAfter(withType<SpotlessTask>(), withType<SpotlessCheck>())
        }
    }

    test {
        useJUnitPlatform()
        // The synthetic projects apply the real version catalog rather than a stub, so a test fails
        // when a plugin asks for a key the project does not actually have.
        systemProperty(
            "bkp.test.versionCatalog",
            isolated.rootProject.projectDirectory
                .file("../gradle/libs.versions.toml")
                .asFile
                .absolutePath,
        )
    }
}

gradlePlugin {
    plugins {
        register("bkpAndroidApp") {
            id = "bkp.android.app"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidAppPlugin"
        }
        register("bkpAndroidAppCompose") {
            id = "bkp.android.app.compose"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidAppComposePlugin"
        }
        register("bkpAndroidAppFirebase") {
            id = "bkp.android.app.firebase"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidAppFirebasePlugin"
        }
        register("bkpAndroidLib") {
            id = "bkp.android.lib"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidLibPlugin"
        }
        register("bkpAndroidTest") {
            id = "bkp.android.test"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpAndroidTestPlugin"
        }
        register("bkpKmpLib") {
            id = "bkp.kmp.lib"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpLibPlugin"
        }
        register("bkpKmpFeature") {
            id = "bkp.kmp.feature"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpFeaturePlugin"
        }
        register("bkpKmpApp") {
            id = "bkp.kmp.app"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpAppPlugin"
        }
        register("bkpKmpUi") {
            id = "bkp.kmp.ui"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpKmpUiPlugin"
        }
        register("bkpKmpCapabilityApi") {
            id = "bkp.kmp.capability.api"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpCapabilityApiPlugin"
        }
        register("bkpKmpCapabilityImpl") {
            id = "bkp.kmp.capability.impl"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpCapabilityImplPlugin"
        }
        register("bkpKmpFoundationApi") {
            id = "bkp.kmp.foundation.api"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpFoundationApiPlugin"
        }
        register("bkpKmpFoundationRuntime") {
            id = "bkp.kmp.foundation.runtime"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpFoundationRuntimePlugin"
        }
        register("bkpKmpPlatform") {
            id = "bkp.kmp.platform"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpPlatformPlugin"
        }
        register("bkpKmpPlatformApi") {
            id = "bkp.kmp.platform.api"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpPlatformApiPlugin"
        }
        register("bkpKmpPlatformImpl") {
            id = "bkp.kmp.platform.impl"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpPlatformImplPlugin"
        }
        register("bkpKmpStorage") {
            id = "bkp.kmp.storage"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpStoragePlugin"
        }
        register("bkpKmpTestkit") {
            id = "bkp.kmp.testkit"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpKmpTestkitPlugin"
        }
        register("bkpDesktopApp") {
            id = "bkp.desktop.app"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.module.BkpDesktopAppPlugin"
        }
        register("bkpWebApp") {
            id = "bkp.web.app"
            implementationClass = "dev.mayankmkh.basekmpproject.convention.module.BkpWebAppPlugin"
        }
        register("bkpQualityStyle") {
            id = "bkp.quality.style"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.quality.BkpQualityStylePlugin"
        }
        register("bkpQualityLint") {
            id = "bkp.quality.lint"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.quality.BkpQualityLintPlugin"
        }
        register("bkpValidationGraph") {
            id = "bkp.validation.graph"
            implementationClass =
                "dev.mayankmkh.basekmpproject.convention.validation.BkpValidationGraphPlugin"
        }
    }
}
