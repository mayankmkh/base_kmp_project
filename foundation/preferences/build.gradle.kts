import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.bkp.kmp.foundation.runtime)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    bkpTargets { default() }

    // Android and desktop share the DataStore assembly; only the directory differs (preferences.md
    // section 8.3). Declared through the template rather than with `dependsOn`, which would switch
    // the default hierarchy off for iOS and web.
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
        jvmTest {
            dependencies {
                implementation(libs.touchlab.kermit.test)
            }
        }
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(projects.foundation.runtime)
                implementation(libs.androidx.datastore.core)
                implementation(libs.androidx.datastore.core.okio)
                implementation(libs.androidx.datastore.preferences.core)
                // `api` because `KSerializer` sits on `PreferenceStores.openDocument` and the
                // catalog has no separate serialization-core alias.
                api(libs.kotlinx.serialization.json)
                // `implementation` because `Logger` reaches the public surface only as the
                // parameter of `preferenceStores`, and its one caller is the App composition root,
                // which declares Kermit itself.
                implementation(libs.touchlab.kermit)
            }
        }
    }
}
