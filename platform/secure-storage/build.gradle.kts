import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.bkp.kmp.platform)
}

kotlin {
    explicitApi()
    bkpTargets { default() }

    // Android and desktop share the DataStore assembly and the JSON serializer; only the encryption
    // wrapper and the directory differ (preferences.md section 8.3). Declared through the template
    // rather than with `dependsOn`, which would switch the default hierarchy off for iOS and web.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                // `withAndroidTarget()` only knows the old Android target; the AGP KMP library
                // target is recognised by its platform type.
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(projects.foundation.runtime)
            }
        }
        val jvmAndAndroidMain by getting {
            dependencies {
                implementation(libs.androidx.datastore.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.datastore.tink)
                implementation(libs.tink.android)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.androidx.datastore.tink)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.tink)
            }
        }
    }
}
