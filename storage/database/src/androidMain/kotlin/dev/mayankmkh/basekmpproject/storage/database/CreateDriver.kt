package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase

// `synchronous()` adapts the asynchronously generated schema to the blocking Android driver.
// `generateAsync` describes the query API while Android SQLite remains synchronous.
internal actual suspend fun createDriver(context: PlatformContext): SqlDriver =
    AndroidSqliteDriver(AppDatabase.Schema.synchronous(), context.appContext, DatabaseName)
