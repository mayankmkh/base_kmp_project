plugins {
    alias(libs.plugins.bkp.kmp.foundation.api)
}

bkpModule {
    features {
        compose()
    }
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            }
        }
    }
}
