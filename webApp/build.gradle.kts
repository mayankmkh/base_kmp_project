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
        wasmJsMain {
            dependencies {
                implementation(libs.navigation3.browser)

                // Required by `webpack.config.d/sqljs-config.js`, which emits the `sql-wasm.wasm`
                // that `:shared:libs:database`'s web driver loads at runtime.
                implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            }
        }
    }
}
