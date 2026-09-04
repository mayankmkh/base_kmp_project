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
                implementation(projects.foundation.network)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.michael.bull.kotlin.result)
            }
        }
        jvmTest
    }
}
