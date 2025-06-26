plugins {
    alias(libs.plugins.basekmpproject.shared.library)
}

kotlin {
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
