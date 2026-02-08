plugins {
    alias(libs.plugins.bkp.kmp.lib)
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
