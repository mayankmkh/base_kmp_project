plugins {
    alias(libs.plugins.bkp.kmp.foundation.runtime)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.foundation.resource)
                implementation(projects.foundation.runtime)
                api(libs.kotlinx.coroutines.core)
                api(libs.michael.bull.kotlin.result)
                api(libs.touchlab.kermit)
                implementation(projects.foundation.network)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.touchlab.kermit.test)
            }
        }
        jvmTest
    }
}
