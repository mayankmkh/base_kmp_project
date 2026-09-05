plugins {
    alias(libs.plugins.bkp.kmp.feature)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                // FeatureInstanceKey appears in this module's public signatures, so it is `api`.
                api(projects.foundation.presentation)
                implementation(projects.foundation.resource)
                // A Feature depends on the Capability's API only. Depending on an `-impl` module
                // is denied by DEP-ROLE-DENIED.
                api(projects.capability.todosApi)
                implementation(projects.ui.designSystem)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.testkit.common)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.koin.test)
            }
        }
    }
}
