plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.postsApi)
                implementation(projects.foundation.network)
                implementation(projects.foundation.resource)
                implementation(projects.foundation.runtime)
                implementation(projects.platform.connectivity)
                implementation(projects.storage.database)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.michael.bull.kotlin.result)
                implementation(libs.store5)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}
