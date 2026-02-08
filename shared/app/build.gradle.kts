import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

plugins {
    alias(libs.plugins.bkp.kmp.feature.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinCocoapods)
}

extensions.configure<BkpModuleExtension> {
    features.cocoapods.set(true)
    cocoapods.frameworkBaseName.set("SharedApp")
    cocoapods.iosDeploymentTarget.set("16.0")
    cocoapods.podfilePath.set("iosApp/Podfile")
}

val bkpModule = extensions.getByType<BkpModuleExtension>()

kotlin {
    cocoapods {
        // Required properties
        // Specify the required Pod version here. Otherwise, the Gradle project version is used.
        version = "1.0"
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"
        ios.deploymentTarget = bkpModule.cocoapods.iosDeploymentTarget.get()

        // Optional properties
        // Configure the Pod name here instead of changing the Gradle project name
        name = bkpModule.cocoapods.frameworkBaseName.get()

        framework {
            // Required properties
            // Framework name configuration. Use this property instead of deprecated 'frameworkName'
            baseName = bkpModule.cocoapods.frameworkBaseName.get()

            export(libs.decompose.decompose)
            export(libs.essenty.lifecycle)
        }

        podfile = rootProject.file(bkpModule.cocoapods.podfilePath.get())
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
