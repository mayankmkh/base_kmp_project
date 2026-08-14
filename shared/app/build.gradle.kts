import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.bkp.kmp.feature.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // The iOS targets themselves are registered by the convention plugin; this only adds the
    // framework binary Xcode consumes. Static because the app links it directly via
    // `embedAndSignAppleFrameworkForXcode`.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedApp"
            isStatic = true

            // Without this the compiler cannot infer a bundle ID and falls back to the bundle name
            // (`SharedApp`), warning on every link. The bundle ID identifies the framework during
            // crash symbolication, so pin it rather than inherit the fallback.
            binaryOption("bundleId", "dev.mayankmkh.basekmpproject.shared.app")

            // Kotlin/Native inlining pass that runs before code generation, ahead of LLVM's own
            // inliner. 40 is JetBrains' recommended compromise -- their benchmarks put it at ~9.5%
            // runtime improvement. Measured here it costs 40s on the release link and shrinks the
            // binary slightly (inlined callees become dead and get stripped). Release-only: the
            // debug link pays 5s of the same cost for a binary that never ships. Experimental.
            if (buildType == NativeBuildType.RELEASE) {
                binaryOption("preCodegenInlineThreshold", "40")
            }

            export(libs.decompose.decompose)
            export(libs.essenty.lifecycle)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                with(projects.shared.features) {
                    implementation(list)
                    implementation(details)
                }
                with(projects.shared.libs) {
                    implementation(prefs)
                }
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.touchlab.kermit)
                api(libs.decompose.decompose)
                api(libs.essenty.lifecycle)
                api(libs.decompose.extensions.compose)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
