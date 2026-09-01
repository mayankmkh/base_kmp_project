plugins {
    alias(libs.plugins.bkp.kmp.feature.compose)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.libs.database)
                implementation(projects.shared.libs.posts)
                implementation(libs.store5)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}
