plugins {
    alias(libs.plugins.bkp.kmp.lib)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.coroutinesX)

                implementation(libs.androidx.datastore.core)
                implementation(libs.androidx.datastore.core.okio)
                implementation(libs.androidx.datastore.preferences.core)
            }
        }
    }
}
