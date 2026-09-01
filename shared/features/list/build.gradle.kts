plugins {
    alias(libs.plugins.bkp.kmp.feature.compose)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                // The two halves of the posts data source. The repository in this module is what
                // pairs them into an offline-first read.
                implementation(projects.shared.libs.database)
                implementation(projects.shared.libs.posts)

                // Tells the view model when there is a network again, which is what turns the
                // cached read into stale-while-revalidate rather than just stale.
                implementation(projects.shared.libs.connectivity)
                implementation(libs.store5)
            }
        }
        commonTest {
            dependencies {
                // Answers HTTP without a network, so the repository test drives the real Ktor
                // plugin stack rather than a hand-written fake of it.
                implementation(libs.ktor.client.mock)
            }
        }
        jvmTest {
            dependencies {
                // The in-memory JDBC driver behind `createInMemoryDriver`, so the repository test
                // gets a real SQLDelight database with no file to clean up.
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}
