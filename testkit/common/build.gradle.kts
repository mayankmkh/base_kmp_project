plugins {
    alias(libs.plugins.bkp.kmp.testkit)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.capability.postsApi)
                api(projects.foundation.resource)
                api(projects.foundation.runtime)
                api(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
    }
}
