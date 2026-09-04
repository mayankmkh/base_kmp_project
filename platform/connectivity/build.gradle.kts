plugins {
    alias(libs.plugins.bkp.kmp.platform)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.foundation.runtime)
                // `api`: `ConnectivityMonitor` hands back a `Flow`, so a caller cannot use this
                // module without it.
                api(libs.kotlinx.coroutines.core)
            }
        }
        wasmJsMain {
            dependencies {
                // `window` and its `online`/`offline` events; the browser is the only target whose
                // answer comes from the page rather than from a system service.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
