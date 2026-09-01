plugins {
    alias(libs.plugins.bkp.kmp.lib)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                // `api`: `PostsApi` returns `Result<_, ApiError>`, so callers cannot handle a
                // failure without seeing the networking module's error type.
                api(projects.shared.libs.networking)
                api(libs.michael.bull.kotlin.result)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                // Swaps the engine rather than the client, so tests exercise the real plugin stack.
                implementation(libs.ktor.client.mock)
            }
        }
    }
}
