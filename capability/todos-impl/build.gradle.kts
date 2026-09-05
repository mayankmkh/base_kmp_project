plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// When this Capability needs a table:
// 1. Apply `alias(libs.plugins.sqldelight)` and create AppDatabase with packageName
//    `<pkg>.db` and `generateAsync = true`.
// 2. Put .sq/.sqm in `src/commonMain/sqldelight/<pkg dir>/db/` with repo-wide-unique
//    migration numbers. Keep membership or ordering in its own table next to the entity rows, and
//    write the synchronized marker in the same transaction as the rows.
// 3. Build the generated database once with `LazyDatabase` over the app's `SqlDriverProvider`.
// 4. Add the module as both a SQLDelight `dependency(project(...))` and normal `implementation`
//    in `:storage:database` by hand.
// 5. Keep `.sq` equal to the migrated shape; verification runs in `:storage:database`. Run
//    `./gradlew :storage:database:verifyCommonMainAppDatabaseMigration`.
// When it calls a backend, add `implementation(projects.foundation.network)` and map the typed
// `NetworkFailure` with the logging `toOutcome()` bridge from `:foundation:resource-runtime`.

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.todosApi)
                implementation(projects.foundation.network)
                implementation(projects.foundation.preferences)
                implementation(projects.foundation.resource)
                implementation(projects.foundation.resourceRuntime)
                implementation(projects.foundation.runtime)
                implementation(projects.foundation.sqldelight)
                implementation(projects.platform.connectivity)
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.async.extensions)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.michael.bull.kotlin.result)
                implementation(libs.touchlab.kermit)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(projects.testkit.common)
            }
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("dev.mayankmkh.basekmpproject.capability.todos.impl.db")
            generateAsync.set(true)
        }
    }
}
