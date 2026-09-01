plugins {
    alias(libs.plugins.bkp.kmp.lib)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.androidx.lifecycle.viewmodel)
                api(libs.michael.bull.kotlin.result)
                implementation(projects.shared.libs.coroutinesX)
            }
        }
    }
}
