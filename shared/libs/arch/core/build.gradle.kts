plugins {
    alias(libs.plugins.bkp.kmp.lib)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.store5)
                api(libs.androidx.lifecycle.viewmodel)
                api(libs.michael.bull.kotlin.result)
                implementation(projects.shared.libs.coroutinesX)
            }
        }
    }
}
