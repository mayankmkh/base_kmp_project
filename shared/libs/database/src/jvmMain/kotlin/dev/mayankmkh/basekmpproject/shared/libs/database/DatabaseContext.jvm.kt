package dev.mayankmkh.basekmpproject.shared.libs.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.shared.libs.database.generated.PostsDatabase
import java.io.File

actual class DatabaseContext

/**
 * Opens the desktop database under the user's home directory.
 *
 * Unlike the Android and iOS drivers, the JDBC one does nothing about schema: pointed at a path it
 * opens whatever is there, including an empty file. So creation is conditional on the file not
 * having existed -- running `Schema.create` against a populated database would fail on the first
 * `CREATE TABLE`.
 */
internal actual suspend fun createDriver(context: DatabaseContext): SqlDriver {
    val file = File(System.getProperty("user.home"), ".base_kmp_project/$DatabaseName")
    val isNew = !file.exists()
    file.parentFile?.mkdirs()

    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    if (isNew) {
        PostsDatabase.Schema.create(driver).await()
    }
    return driver
}

/**
 * A throwaway database that lives only as long as the process.
 *
 * Kept in `main` rather than `test` so the desktop app can be started against a clean cache, and so
 * tests in other modules can reach it without depending on this module's test fixtures.
 */
suspend fun createInMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { PostsDatabase.Schema.create(it).await() }

/**
 * A [PostsLocalStore] over [createInMemoryDriver], which is all a test needs from this module.
 *
 * Here for the same reason [createInMemoryDriver] is, and one step further: assembling the store by
 * hand means knowing it wraps a [PostsDatabaseSource] around a generated `PostsDatabase`, which is
 * this module's business rather than its callers'.
 */
suspend fun createInMemoryPostsLocalStore(): PostsLocalStore {
    val database = PostsDatabase(createInMemoryDriver())
    return PostsLocalStore(PostsDatabaseSource { database })
}
