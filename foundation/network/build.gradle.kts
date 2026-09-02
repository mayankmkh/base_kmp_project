plugins {
    alias(libs.plugins.bkp.kmp.foundation.runtime)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.ktor.client.core)
                api(libs.ktor.client.auth)
                api(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.michael.bull.kotlin.result)
            }
        }
        commonTest {
            dependencies {
                // Swaps the engine rather than the client, so tests exercise the real plugin stack.
                implementation(libs.ktor.client.mock)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        jvmMain {
            dependencies {
                // Desktop needs an engine of its own; without one `createHttpClient` finds nothing
                // on the classpath and throws on the first call.
                implementation(libs.ktor.client.okhttp)
            }
        }
        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        wasmJsMain {
            dependencies {
                // Named for Kotlin/JS but published for wasmJs too; the engine is the browser's
                // own `fetch` either way.
                implementation(libs.ktor.client.js)
            }
        }
    }
}
