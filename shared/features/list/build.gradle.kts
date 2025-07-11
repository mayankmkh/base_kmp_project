import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.basekmpproject.shared.feature)
    alias(libs.plugins.basekmpproject.shared.library.compose)
    alias(libs.plugins.basekmpproject.android.library.compose)
    alias(libs.plugins.basekmpproject.android.feature)
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.prefs)
                implementation(compose.materialIconsExtended)
            }
        }
    }
}
