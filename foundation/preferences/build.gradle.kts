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
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                implementation(projects.foundation.runtime)
                implementation(libs.androidx.datastore.core)
                implementation(libs.androidx.datastore.core.okio)
                implementation(libs.androidx.datastore.preferences.core)
                // `api` because `KSerializer` sits on the public `openDocumentStore` signature and
                // the catalog has no separate serialization-core alias.
                api(libs.kotlinx.serialization.json)
            }
        }
    }
}
