package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase

// The native driver resolves the file under Application Support/databases and migrates it itself.
internal actual suspend fun createDriver(context: PlatformContext): SqlDriver =
    NativeSqliteDriver(AppDatabase.Schema.synchronous(), DatabaseName)
