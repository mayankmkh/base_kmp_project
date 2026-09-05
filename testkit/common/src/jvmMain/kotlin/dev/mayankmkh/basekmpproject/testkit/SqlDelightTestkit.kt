package dev.mayankmkh.basekmpproject.testkit

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider

/** Creates a schema-initialised JDBC SQLite driver backed only by memory. */
fun inMemorySqliteDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = schema.synchronous())

/** Adapts this already-open test driver to the application driver contract. */
fun SqlDriver.asProvider(): SqlDriverProvider = SqlDriverProvider { this }
