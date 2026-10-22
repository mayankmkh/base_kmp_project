plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.identityApi)
                implementation(projects.foundation.network)
                implementation(projects.foundation.runtime)
                implementation(projects.platform.secureStorage)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.touchlab.kermit)
            }
        }
    }
}
