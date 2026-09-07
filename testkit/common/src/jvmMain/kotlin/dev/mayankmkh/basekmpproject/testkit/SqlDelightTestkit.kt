package dev.mayankmkh.basekmpproject.testkit

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Creates a schema-initialised JDBC SQLite driver backed only by memory. */
fun inMemorySqliteDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = schema.synchronous())

/**
 * Adapts this already-open test driver to the application driver contract.
 *
 * The default lane is `Unconfined` so that the statements `LazyDatabase` moves off the collector
 * still run on the test's own thread, keeping a `runTest` body in the order it was written.
 */
fun SqlDriver.asProvider(lane: CoroutineDispatcher = Dispatchers.Unconfined): SqlDriverProvider =
    OpenSqlDriverProvider(this, lane)

private class OpenSqlDriverProvider(
    private val open: SqlDriver,
    override val dispatcher: CoroutineDispatcher,
) : SqlDriverProvider {
    override suspend fun driver(): SqlDriver = open
}
