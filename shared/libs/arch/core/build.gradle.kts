plugins {
    alias(libs.plugins.bkp.kmp.lib)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.michael.bull.kotlin.result)
                implementation(projects.shared.libs.coroutinesX)
                implementation(libs.essenty.instanceKeeper)
                implementation(libs.essenty.lifecycle)
                implementation(libs.essenty.lifecycle.coroutines)
            }
        }
    }
}
