plugins {
    alias(libs.plugins.basekmpproject.shared.library)
    alias(libs.plugins.basekmpproject.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget()

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
