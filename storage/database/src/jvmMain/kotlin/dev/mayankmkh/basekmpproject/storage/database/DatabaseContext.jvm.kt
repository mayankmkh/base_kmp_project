package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.storage.database.generated.PostsDatabase
import java.io.File

actual class DatabaseContext

/**
 * Opens the desktop database under the user's home directory.
 *
 * The schema-aware JDBC factory uses SQLite's `user_version` for creation and migration. Databases
 * made before that factory was adopted have version zero despite containing the version-one schema,
 * so existing files use `migrateEmptySchema`: migration 1 adds feed state without trying to
 * recreate `post`.
 */
internal actual suspend fun createDriver(context: DatabaseContext): SqlDriver {
    val file = File(System.getProperty("user.home"), ".base_kmp_project/$DatabaseName")
    val isNew = !file.exists()
    file.parentFile?.mkdirs()

    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.absolutePath}",
        schema = PostsDatabase.Schema.synchronous(),
        migrateEmptySchema = !isNew,
    )
}

/**
 * A throwaway database that lives only as long as the process.
 *
 * Kept in `main` rather than `test` so the desktop app can be started against a clean cache, and so
 * tests in other modules can reach it without depending on this module's test fixtures.
 */
fun createInMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        schema = PostsDatabase.Schema.synchronous(),
    )

/**
 * A [PostsLocalStore] over [createInMemoryDriver], which is all a test needs from this module.
 *
 * Here for the same reason [createInMemoryDriver] is, and one step further: assembling the store by
 * hand means knowing it wraps a [PostsDatabaseSource] around a generated `PostsDatabase`, which is
 * this module's business rather than its callers'.
 */
fun createInMemoryPostsLocalStore(): PostsLocalStore {
    val database = PostsDatabase(createInMemoryDriver())
    return PostsLocalStore(PostsDatabaseSource { database })
}
