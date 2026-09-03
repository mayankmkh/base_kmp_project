plugins {
    alias(libs.plugins.bkp.kmp.capability.api)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.foundation.resource)
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
