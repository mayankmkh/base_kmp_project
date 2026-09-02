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
            }
        }
        commonTest {
            dependencies {
                implementation(projects.testkit.common)
            }
        }
    }
}
