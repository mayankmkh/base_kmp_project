package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Opens the desktop database in the application's own data directory, so each application id --
 * production and staging -- keeps its own file beside its preferences and secrets. The schema-aware
 * JDBC factory creates or migrates the file from SQLite's `user_version`.
 */
internal actual suspend fun createDriver(context: PlatformContext): SqlDriver {
    val file = applicationDataDirectory(context.applicationId).resolve(DatabaseName)
    file.parentFile?.mkdirs()
    moveLegacyDatabase(legacyDatabaseFile(), file)

    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.absolutePath}",
        schema = AppDatabase.Schema.synchronous(),
    )
}

/** Where every application id shared one database before each had its own. */
internal fun legacyDatabaseFile(): File =
    File(System.getProperty("user.home"), ".base_kmp_project/$DatabaseName")

/**
 * Moves the shared database to [target] once, for the first application id that opens after the
 * upgrade. It is a cache, but local writes not yet synced would otherwise vanish.
 *
 * Two application ids launching together serialise on a lock file beside the shared database and
 * check again under it, so only one of them takes it. Every file is copied under a temporary name
 * in the target directory and renamed into place atomically, so a move across volumes cut short
 * never leaves a partial file under a real name. The journals go first, so SQLite can still roll
 * back an interrupted write, and the database last, so a launch that finds no target database
 * finishes an earlier attempt: a journal already in place is replaced by the one still in the
 * shared directory, or kept when that one has already moved. A crash between the last rename and
 * the delete of the shared database leaves a copy behind, which the next id to open without a
 * database of its own takes as well; for a cache that window is accepted. So is a crash between the
 * journal moves and the database move, after which a different application id can take the database
 * without its hot journal: the database is a cache in DELETE journal mode. The lock file stays, as
 * deleting it would race the next launch.
 */
internal fun moveLegacyDatabase(legacy: File, target: File) {
    if (target.exists() || !legacy.isFile) return
    val lockFile = File(legacy.path + ".move-lock").toPath()
    FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        channel.lock().use {
            if (target.exists() || !legacy.isFile) return
            for (suffix in listOf("-journal", "-wal", "-shm", "")) {
                val from = File(legacy.path + suffix).toPath()
                if (!Files.isRegularFile(from)) continue
                val staged = File(target.path + suffix + ".moving").toPath()
                Files.copy(from, staged, StandardCopyOption.REPLACE_EXISTING)
                Files.move(
                    staged,
                    File(target.path + suffix).toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
                Files.delete(from)
            }
        }
    }
}
