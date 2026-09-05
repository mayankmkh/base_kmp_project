plugins {
    alias(libs.plugins.bkp.kmp.ui)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.foundation.resource)
            }
        }
    }
}

compose.resources {
    publicResClass = true
}
