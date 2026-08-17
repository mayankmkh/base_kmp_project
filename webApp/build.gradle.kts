plugins {
    alias(libs.plugins.bkp.web.app)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.app)
            }
        }
    }
}
