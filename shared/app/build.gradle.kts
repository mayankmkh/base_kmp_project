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
