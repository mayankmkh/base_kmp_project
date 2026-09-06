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
                implementation(projects.capability.todosApi)
                implementation(projects.foundation.network)
                implementation(projects.foundation.preferences)
                implementation(projects.foundation.resource)
                implementation(projects.foundation.resourceRuntime)
                implementation(projects.foundation.runtime)
                implementation(projects.foundation.sqldelight)
                implementation(projects.platform.connectivity)
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.async.extensions)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.michael.bull.kotlin.result)
                implementation(libs.touchlab.kermit)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(projects.testkit.common)
            }
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("dev.mayankmkh.basekmpproject.capability.todos.impl.db")
            generateAsync.set(true)
        }
    }
}
