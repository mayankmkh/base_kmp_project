plugins {
    alias(libs.plugins.bkp.web.app)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.app.shared)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.navigation3.browser)

                // Required by `webpack.config.d/sqljs-config.js`, which emits the `sql-wasm.wasm`
                // that `:storage:database`'s web driver loads at runtime.
                implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            }
        }
    }
}
