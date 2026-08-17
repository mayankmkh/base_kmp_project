plugins {
    alias(libs.plugins.bkp.kmp.lib)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.coroutinesX)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.datastore.core)
                implementation(libs.androidx.datastore.core.okio)
                implementation(libs.androidx.datastore.preferences.core)
            }
        }
    }
}
