plugins {
    alias(libs.plugins.basekmpproject.shared.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
