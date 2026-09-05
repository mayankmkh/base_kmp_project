import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.bkp.kmp.app)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    bkpTargets {
        default()

        // Refines the targets `default()` already declared rather than adding a second pair, and
        // gives them the framework binary Xcode consumes. Static because the app links it directly
        // via `embedAndSignAppleFrameworkForXcode`.
        ios {
            binaries.framework {
                baseName = "SharedApp"
                isStatic = true

                // Without this the compiler cannot infer a bundle ID and falls back to the bundle
                // name (`SharedApp`), warning on every link. The bundle ID identifies the framework
                // during crash symbolication, so pin it rather than inherit the fallback.
                binaryOption("bundleId", "dev.mayankmkh.basekmpproject.app.shared")

                // Kotlin/Native inlining pass that runs before code generation, ahead of LLVM's own
                // inliner. 40 is JetBrains' recommended compromise -- their benchmarks put it at
                // ~9.5% runtime improvement. Measured here it costs 40s on the release link and
                // shrinks the binary slightly (inlined callees become dead and get stripped).
                // Release-only: the debug link pays 5s of the same cost for a binary that never
                // ships. Experimental.
                if (buildType == NativeBuildType.RELEASE) {
                    binaryOption("preCodegenInlineThreshold", "40")
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.foundation.runtime)
                implementation(projects.foundation.network)
                implementation(projects.foundation.preferences)
                implementation(projects.foundation.presentation)
                implementation(projects.ui.designSystem)

                implementation(projects.capability.identityApi)
                implementation(projects.capability.identityImpl)
                implementation(projects.capability.postsApi)
                implementation(projects.capability.postsImpl)
                implementation(projects.capability.todosApi)
                implementation(projects.capability.todosImpl)
                implementation(projects.feature.posts)
                implementation(projects.feature.todos)
                // The app module owns the object graph, so it hands the data layer a shared
                // `PlatformContext` and a configured `HttpClient`.
                implementation(projects.storage.database)
                // Same story: the app supplies the platform connectivity mechanism.
                implementation(projects.platform.connectivity)
                implementation(projects.platform.secureStorage)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.touchlab.kermit)
                implementation(libs.touchlab.kermit.koin)
                api(libs.androidx.navigation3.runtime)
                implementation(libs.jetbrains.androidx.navigation3.ui)
                implementation(libs.compose.material3.adaptive.navigation3)
                implementation(libs.compose.material3.adaptive.navigationSuite)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
        jvmTest {
            dependencies {
                // JVM-only: Koin's graph verification reflects over constructors, so there is no
                // `commonTest` home for it even though the artifact is multiplatform.
                implementation(libs.koin.test)

                // Lets the navigation test drive the real object graph without a network: the
                // `HttpClient` binding is overridden with one on a `MockEngine`.
                implementation(libs.ktor.client.mock)
            }
        }
    }
}
