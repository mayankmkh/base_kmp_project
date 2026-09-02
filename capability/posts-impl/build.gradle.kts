plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
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
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.async.extensions)
                implementation(libs.sqldelight.coroutines.extensions)
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

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("dev.mayankmkh.basekmpproject.capability.posts.impl.db")
            generateAsync.set(true)
        }
    }
}
