package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import java.io.File

/**
 * Opens the desktop database under the user's home directory. The schema-aware JDBC factory creates
 * or migrates the file from SQLite's `user_version`.
 */
internal actual suspend fun createDriver(context: PlatformContext): SqlDriver {
    val file = File(System.getProperty("user.home"), ".base_kmp_project/$DatabaseName")
    file.parentFile?.mkdirs()

    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.absolutePath}",
        schema = AppDatabase.Schema.synchronous(),
    )
}
