plugins {
    alias(libs.plugins.bkp.kmp.lib)
    alias(libs.plugins.sqldelight)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                // `api`, not `implementation`: the generated `PostsDatabase` and its query types
                // are
                // this module's public surface, and they are declared in terms of the runtime's
                // `Query`/`SuspendingTransacter`.
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.async.extensions)
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.sqldelight.android.driver)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
        iosMain {
            dependencies {
                implementation(libs.sqldelight.native.driver)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.sqldelight.web.worker.driver)
                implementation(libs.kotlinx.browser)
                // The worker script and the wasm blob it loads are npm-side artifacts; there is
                // no Maven equivalent. Getting them into a browser bundle also needs webpack
                // configuration, which a library module cannot supply -- see
                // `webApp/webpack.config.d/sqljs-config.js`.
                implementation(
                    npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqldelight.get())
                )
                implementation(npm("sql.js", "1.8.0"))
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("PostsDatabase") {
            packageName.set("dev.mayankmkh.basekmpproject.shared.libs.database.generated")
            // Required, not a preference: the only wasmJs driver is the Web Worker one, and a
            // worker can only be talked to asynchronously. Turning this on makes the generated
            // query API suspending on every target, so common code has one shape to write against.
            generateAsync.set(true)
        }
    }
}
