plugins {
    alias(libs.plugins.bkp.kmp.feature.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.prefs)
            }
        }
    }
}
