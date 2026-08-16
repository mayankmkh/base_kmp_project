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
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.mayankmkh.basekmpproject.convention.core.androidSdkConfig
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

/**
 * Compiler args that have to hold for every Kotlin compilation in the project -- common metadata,
 * native, JVM and Android alike -- so that all source sets are analysed under the same language
 * rules.
 */
private val COMMON_FREE_COMPILER_ARGS = listOf(
    // Enable experimental coroutines APIs, including Flow
    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    /**
     * Remove this arg after Phase 3.
     * https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-consistent-copy-visibility/#deprecation-timeline
     *
     * Deprecation timeline
     * Phase 3. (Still not reached as of Kotlin 2.4 -- dropping the flag reinstates the
     * "Non-public primary constructor is exposed via the generated 'copy()'" warning.)
     * The default changes.
     * Unless ExposedCopyVisibility is used, the generated 'copy' method has the same visibility as the primary constructor.
     * The binary signature changes. The error on the declaration is no longer reported.
     * '-Xconsistent-data-class-copy-visibility' compiler flag and ConsistentCopyVisibility annotation are now unnecessary.
     */
    "-Xconsistent-data-class-copy-visibility",
    // `expect`/`actual` classes are still Beta and warn on every `actual` declaration. The design
    // deliberately uses them (see `PrefContext`), so accept them project-wide instead of annotating
    // each actual. Drop this once KT-61573 stabilises them.
    "-Xexpect-actual-classes",
)

/**
 * Configure base Kotlin with Android options
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    // AGP 9's `CommonExtension` is no longer generic and only exposes getters — the block DSL
    // (`defaultConfig { }`, `compileOptions { }`) lives on the concrete extension types, so the
    // shared helper configures the nested objects through property access instead.
    commonExtension.apply {
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()

        defaultConfig.minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()

        // Up to Java 11 APIs are available through desugaring
        // https://developer.android.com/studio/write/java11-minimal-support-table
        compileOptions.sourceCompatibility = JavaVersion.VERSION_11
        compileOptions.targetCompatibility = JavaVersion.VERSION_11
        compileOptions.isCoreLibraryDesugaringEnabled = true
    }

    configureKotlin()

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("android.desugarJdkLibs").get())
    }
}

/**
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        // Up to Java 11 APIs are available through desugaring
        // https://developer.android.com/studio/write/java11-minimal-support-table
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    configureKotlin()
}

internal fun Project.configureKotlinMultiplatformAndroidLibrary(
    kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) {
    val sdk = androidSdkConfig()

    // The KMP Android plugin deliberately does not register a project-level extension: the target
    // *is* the DSL object, so it is reached through the target container rather than by name.
    // https://developer.android.com/kotlin/multiplatform/kmp-integration
    kotlinMultiplatformExtension.targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
        compileSdk = sdk.compileSdk
        minSdk = sdk.minSdk
        namespace = "dev.mayankmkh.basekmpproject" + project.path.replace(':', '.').replace('-', '.')

        // KMP Android resources are opt-in in AGP.
        androidResources.enable = true
        enableCoreLibraryDesugaring = true

        // Host tests are opt-in too, and without them `commonTest` has no Android compilation at
        // all -- shared tests would run on jvm and ios but silently skip the Android target.
        withHostTest { isIncludeAndroidResources = true }

        // Device tests are opt-in as well. No shared module has instrumented tests today, but
        // without this an `src/androidDeviceTest` directory added later is simply never compiled --
        // no source set, no task, no error. `sourceSetTreeName = "test"` is what puts it under the
        // same `commonTest` tree as the host tests.
        withDeviceTestBuilder { sourceSetTreeName = "test" }
            .configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                // `targetSdk` has no `defaultConfig` equivalent on a KMP Android library; the
                // device test compilation is the only place it applies, and without it these
                // modules silently drop off the catalog value the other primaries use.
                targetSdk { version = release(sdk.targetSdk) }
            }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("android.desugarJdkLibs").get())
    }
}

/**
 * Throttles [KotlinNativeLink] so only one runs at a time.
 *
 * Each Kotlin/Native link forks its own compiler JVM sized by `kotlin.native.jvmArgs`, and the
 * release links need a large heap for the whole-program devirtualization analysis. Letting the
 * iosArm64 and iosSimulatorArm64 links run concurrently means two of those heaps plus the rest
 * of the build, which exhausts memory and fails with an [OutOfMemoryError] -- raising the heap
 * only brings the failure on sooner. Serialising them keeps the peak to a single compiler JVM.
 */
abstract class KotlinNativeLinkThrottle : BuildService<BuildServiceParameters.None>

/**
 * Configure base Kotlin options
 */
internal fun Project.configureKotlin() {
    val nativeLinkThrottle =
        gradle.sharedServices.registerIfAbsent(
            "kotlinNativeLinkThrottle",
            KotlinNativeLinkThrottle::class,
        ) {
            maxParallelUsages = 1
        }
    tasks.withType<KotlinNativeLink>().configureEach {
        usesService(nativeLinkThrottle)
    }

    // Treat all Kotlin warnings as errors (disabled by default)
    // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map {
        it.toBoolean()
    }.orElse(false)

    // `KotlinCompilationTask` covers every Kotlin compilation -- common metadata, native and JVM
    // alike. Scoping this to `KotlinJvmCompile` would compile common and native sources under
    // different language rules than the JVM ones, which is how `@ConsistentCopyVisibility` ended up
    // being simultaneously required (native) and reported as redundant (JVM).
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors = warningsAsErrors
        }
    }

    // Prefer the extension over the tasks: `tasks.withType { }.configureEach { }` only runs once a
    // task is realized. Gradle always realizes them before compiling, so a task-level `addAll` is
    // correct for the build -- but the IDE's Gradle import reads a compilation's `compilerOptions`
    // without realizing its task, so those args never reach the IDE analyzer and it reports
    // warnings (e.g. the `copy()` visibility one on `BaseUrls`) that the build does not.
    // `KotlinMultiplatformExtension.compilerOptions` is populated at configuration time and
    // propagates to every target, including the AGP-registered Android one.
    val kotlinMultiplatform = extensions.findByType<KotlinMultiplatformExtension>()
    if (kotlinMultiplatform != null) {
        kotlinMultiplatform.compilerOptions {
            freeCompilerArgs.addAll(COMMON_FREE_COMPILER_ARGS)
        }
    } else {
        // Pure-Android/JVM modules run on AGP 9's built-in Kotlin and have no KGP extension to hang
        // these off, so they stay on the tasks.
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions {
                freeCompilerArgs.addAll(COMMON_FREE_COMPILER_ARGS)
            }
        }
    }

    // `jvmTarget` only exists on the JVM-flavoured compilations. `KotlinJvmCompile` is the interface
    // implemented both by KGP's own `KotlinCompile` tasks (KMP jvm/android compilations) and by the
    // tasks AGP 9 registers for built-in Kotlin.
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}
