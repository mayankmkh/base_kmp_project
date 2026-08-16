plugins {
    alias(libs.plugins.bkp.kmp.feature.compose)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.prefs)
            }
        }
    }
}
