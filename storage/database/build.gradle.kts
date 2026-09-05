plugins {
    alias(libs.plugins.bkp.kmp.storage)
    alias(libs.plugins.sqldelight)
}

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.postsImpl)
                implementation(projects.capability.todosImpl)
                api(projects.foundation.runtime)
                api(projects.foundation.sqldelight)
                // The assembled database is this module's public surface and its generated
                // interface is declared in terms of SQLDelight runtime types.
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.async.extensions)
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
                // `app/web/webpack.config.d/sqljs-config.js`.
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
        create("AppDatabase") {
            packageName.set("dev.mayankmkh.basekmpproject.storage.database.db")
            // Migrations are verified only here: this compilation unit merges every contributor.
            // Generate a new `<version>.db` there when wanted with
            // `./gradlew :storage:database:generateCommonMainAppDatabaseSchema`.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
            // Required, not a preference: the only wasmJs driver is the Web Worker one, and a
            // worker can only be talked to asynchronously. Turning this on makes the generated
            // query API suspending on every target, so common code has one shape to write against.
            generateAsync.set(true)
            dependency(project(":capability:posts-impl"))
            dependency(project(":capability:todos-impl"))
        }
    }
}
