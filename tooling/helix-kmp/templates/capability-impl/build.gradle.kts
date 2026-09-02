plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.__API_ACCESSOR__)
            }
        }
    }
}
