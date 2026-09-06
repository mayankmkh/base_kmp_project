plugins {
    alias(libs.plugins.bkp.kmp.foundation.api)
}

bkpModule {
    features {
        compose()
    }
}

dependencies {
    "commonMainApi"(platform(libs.koin.bom))
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.koin.core)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.viewmodel.savedstate)
                implementation(libs.koin.compose.viewmodel)
            }
        }
    }
}
