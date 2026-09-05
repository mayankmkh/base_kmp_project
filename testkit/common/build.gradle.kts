plugins {
    alias(libs.plugins.bkp.kmp.testkit)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.capability.postsApi)
                api(projects.capability.todosApi)
                api(projects.foundation.resource)
                api(projects.foundation.runtime)
                api(projects.foundation.sqldelight)
                api(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
        jvmMain {
            dependencies {
                api(libs.sqldelight.sqlite.driver)
            }
        }
    }
}
