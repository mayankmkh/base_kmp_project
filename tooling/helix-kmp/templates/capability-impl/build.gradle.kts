plugins {
    alias(libs.plugins.bkp.kmp.capability.impl)
}

// When this Capability needs a table:
// 1. Apply `alias(libs.plugins.sqldelight)` and create AppDatabase with packageName
//    `<pkg>.db` and `generateAsync = true`.
// 2. Put .sq/.sqm in `src/commonMain/sqldelight/<pkg dir>/db/` with repo-wide-unique
//    migration numbers.
// 3. Expose `fun interface <X>DatabaseSource { suspend fun database(): AppDatabase }`.
// 4. Add the module as both a SQLDelight `dependency(project(...))` and normal `implementation`
//    in `:storage:database`, then bridge the source in `:app:shared`'s `databaseModule`.
// 5. Keep `.sq` equal to the migrated shape; verification runs in `:storage:database`. Run
//    `./gradlew :storage:database:verifyCommonMainAppDatabaseMigration`.

kotlin {
    bkpTargets { default() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.capability.__API_ACCESSOR__)
            }
        }
    }
}
