plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
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
// Network failures cross the logging `toOutcome()` bridge from `:foundation:resource-runtime`.

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.__API_ACCESSOR__)
                implementation(projects.foundation.network)
                implementation(projects.foundation.resource)
                implementation(projects.foundation.resourceRuntime)
                implementation(projects.foundation.runtime)
                implementation(projects.platform.connectivity)
                implementation(libs.michael.bull.kotlin.result)
                implementation(libs.touchlab.kermit)
            }
        }
    }
}
