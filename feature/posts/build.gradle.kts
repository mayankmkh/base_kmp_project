plugins {
    alias(libs.plugins.bkp.kmp.feature)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.capability.postsApi)
                api(projects.foundation.presentation)
                implementation(projects.foundation.resource)
                implementation(projects.ui.designSystem)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.testkit.common)
            }
        }
    }
}
