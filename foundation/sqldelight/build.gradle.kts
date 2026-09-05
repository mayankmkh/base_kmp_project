plugins {
    alias(libs.plugins.bkp.kmp.foundation.runtime)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.async.extensions)
                api(libs.sqldelight.coroutines.extensions)
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
