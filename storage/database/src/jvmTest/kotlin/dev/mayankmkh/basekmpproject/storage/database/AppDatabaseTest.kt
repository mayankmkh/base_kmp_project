package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AppDatabaseTest {

    @Test
    fun `fresh schema composes the posts capability tables`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver).await()

            val tables = driver.sqliteMasterNames("table")
            assertTrue("post" in tables)
            assertTrue("postFeedState" in tables)
            assertTrue("author_id" in driver.tableColumns("post"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun `migrations merge across contributors and preserve version one rows`() = runTest {
        val snapshot = File("src/commonMain/sqldelight/databases/1.db")
        check(snapshot.isFile) {
            "Missing composed migration snapshot: ${snapshot.absolutePath}"
        }
        val directory = createTempDirectory("database-migration").toFile()
        val databaseFile = snapshot.copyTo(File(directory, "migration.db"))
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        try {
            driver.executeSql(
                "INSERT INTO post(id, title, body, position) VALUES ('kept', 'Title', 'Body', 0)"
            )

            AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version).await()

            assertEquals(listOf("kept"), driver.stringColumn("SELECT id FROM post"))
            assertEquals(listOf(0L), driver.longColumn("SELECT author_id FROM post"))
        } finally {
            driver.close()
            directory.deleteRecursively()
        }
    }

    private fun SqlDriver.executeSql(sql: String) {
        execute(null, sql.trimIndent(), 0).value
    }

    private fun SqlDriver.sqliteMasterNames(type: String): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = '$type'") { cursor ->
                cursor.getString(0)!!
            }
            .toSet()

    private fun SqlDriver.tableColumns(table: String): Set<String> =
        query("PRAGMA table_info($table)") { cursor -> cursor.getString(1)!! }.toSet()

    private fun SqlDriver.stringColumn(sql: String): List<String> =
        query(sql) { cursor -> cursor.getString(0)!! }

    private fun SqlDriver.longColumn(sql: String): List<Long> =
        query(sql) { cursor -> cursor.getLong(0)!! }

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
