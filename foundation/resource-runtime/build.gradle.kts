plugins {
    alias(libs.plugins.bkp.kmp.foundation.runtime)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.foundation.resource)
                api(libs.kotlinx.coroutines.core)
                api(libs.michael.bull.kotlin.result)
                api(libs.touchlab.kermit)
                implementation(projects.foundation.network)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }
        jvmTest
    }
}
