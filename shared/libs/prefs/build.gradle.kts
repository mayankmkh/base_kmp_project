plugins {
    alias(libs.plugins.bkp.kmp.lib)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.coroutinesX)

                implementation(libs.russhwolf.multiplatformSettings.core)
                implementation(libs.russhwolf.multiplatformSettings.coroutines)
                implementation(libs.russhwolf.multiplatformSettings.datastore)
                implementation(libs.russhwolf.multiplatformSettings.serialization)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.datastore)
                implementation(libs.androidx.datastore.preferences)
            }
        }
    }
}
