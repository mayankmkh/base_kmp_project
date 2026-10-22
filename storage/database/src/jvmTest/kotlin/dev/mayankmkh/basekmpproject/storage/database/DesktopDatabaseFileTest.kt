package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Ids of their own, and every directory they resolve to removed afterwards: off macOS the data
 * directory follows `APPDATA` or `XDG_CONFIG_HOME` rather than the temporary home, so a fixed id
 * would find the previous run's table there.
 */
class DesktopDatabaseFileTest {
    private val production = "database-file-test-${System.nanoTime()}"
    private val staging = "$production.staging"

    @Test
    fun `each application id opens its own database file`() = runTest {
        withTemporaryHome {
            createDriver(PlatformContext(production)).use { production ->
                production.executeSql("CREATE TABLE onlyHere(x INTEGER)")
            }

            createDriver(PlatformContext(staging)).use { staging ->
                assertFalse("onlyHere" in staging.tableNames())
            }
            createDriver(PlatformContext(production)).use { production ->
                assertTrue("onlyHere" in production.tableNames())
            }
        }
    }

    /**
     * Where the preferences and secrets of the same id already live, not a directory of its own.
     */
    @Test
    fun `the database lands in the application data directory`() = runTest {
        withTemporaryHome {
            createDriver(PlatformContext(production)).use { driver ->
                driver.executeSql("CREATE TABLE placed(x INTEGER)")
            }

            val expected = applicationDataDirectory(production).resolve(DatabaseName)
            assertTrue(expected.isFile, "Expected the database at $expected")
        }
    }

    /**
     * The database every application id used to share moves, once, to the first id that opens: it
     * may hold local writes not yet synced. The next id starts empty, and the move is not repeated.
     */
    @Test
    fun `the shared database of an earlier version moves to the first application id`() = runTest {
        withTemporaryHome {
            val legacy = legacyDatabaseFile()
            legacy.parentFile.mkdirs()
            JdbcSqliteDriver("jdbc:sqlite:${legacy.absolutePath}").use { old ->
                old.executeSql("CREATE TABLE unsynced(x INTEGER)")
            }

            createDriver(PlatformContext(production)).use { production ->
                assertTrue("unsynced" in production.tableNames())
            }
            assertFalse(legacy.exists(), "The shared database was left behind at $legacy")

            createDriver(PlatformContext(staging)).use { staging ->
                assertFalse("unsynced" in staging.tableNames())
            }
        }
    }

    /** SQLite needs the journals beside the database to roll back a write that was cut short. */
    @Test
    fun `the journals of the shared database move with it`() {
        withMoveDirectories { legacy, target ->
            legacy.writeText("database")
            legacy.sibling("-wal").writeText("wal")
            legacy.sibling("-shm").writeText("shm")

            moveLegacyDatabase(legacy, target)

            assertEquals("database", target.readText())
            assertEquals("wal", target.sibling("-wal").readText())
            assertEquals("shm", target.sibling("-shm").readText())
            assertEquals(
                setOf(DatabaseName, "$DatabaseName-wal", "$DatabaseName-shm"),
                target.parentFile.list()?.toSet(),
            )
            assertFalse(legacy.exists() || legacy.sibling("-wal").exists())
        }
    }

    /**
     * A launch that stopped mid-move left the journal already moved, the WAL copied but not yet
     * deleted, and a staged database cut short. The next launch finishes the move rather than
     * failing on what is already in place.
     */
    @Test
    fun `a partial earlier attempt is completed`() {
        withMoveDirectories { legacy, target ->
            legacy.writeText("database")
            legacy.sibling("-wal").writeText("wal")
            target.sibling("-wal").writeText("wal")
            target.sibling("-journal").writeText("journal")
            target.sibling(".moving").writeText("data")

            moveLegacyDatabase(legacy, target)

            assertEquals("database", target.readText())
            assertEquals("wal", target.sibling("-wal").readText())
            assertEquals("journal", target.sibling("-journal").readText())
            assertFalse(target.sibling(".moving").exists())
            assertFalse(legacy.exists() || legacy.sibling("-wal").exists())
        }
    }

    private inline fun withMoveDirectories(block: (legacy: File, target: File) -> Unit) {
        val legacyDirectory = createTempDirectory("legacy-database").toFile()
        val targetDirectory = createTempDirectory("moved-database").toFile()
        try {
            block(legacyDirectory.resolve(DatabaseName), targetDirectory.resolve(DatabaseName))
        } finally {
            legacyDirectory.deleteRecursively()
            targetDirectory.deleteRecursively()
        }
    }

    private fun File.sibling(suffix: String) = File(path + suffix)

    private inline fun withTemporaryHome(block: () -> Unit) {
        val home = createTempDirectory("database-home").toFile()
        val original = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)
        try {
            block()
        } finally {
            applicationDataDirectory(production).deleteRecursively()
            applicationDataDirectory(staging).deleteRecursively()
            System.setProperty("user.home", original)
            home.deleteRecursively()
        }
    }

    private fun SqlDriver.executeSql(sql: String) {
        execute(null, sql, 0).value
    }

    private fun SqlDriver.tableNames(): List<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table'") { it.getString(0)!! }

    private fun <T> SqlDriver.query(sql: String, mapper: (SqlCursor) -> T): List<T> =
        executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    val rows = mutableListOf<T>()
                    while (cursor.next().value) rows += mapper(cursor)
                    QueryResult.Value(rows)
                },
                parameters = 0,
            )
            .value
}
