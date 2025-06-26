plugins {
    alias(libs.plugins.basekmpproject.shared.feature)
    alias(libs.plugins.basekmpproject.shared.library.compose)
    alias(libs.plugins.basekmpproject.android.library.compose)
    alias(libs.plugins.basekmpproject.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinCocoapods)
}

kotlin {

    androidTarget()

    cocoapods {
        // Required properties
        // Specify the required Pod version here. Otherwise, the Gradle project version is used.
        version = "1.0"
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"
        ios.deploymentTarget = "16.0"

        // Optional properties
        // Configure the Pod name here instead of changing the Gradle project name
        name = "SharedApp"

        framework {
            // Required properties
            // Framework name configuration. Use this property instead of deprecated 'frameworkName'
            baseName = "SharedApp"

            export(libs.decompose.decompose)
            export(libs.essenty.lifecycle)
        }

        podfile = project.file("../../iosApp/Podfile")
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
