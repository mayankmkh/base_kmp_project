package dev.mayankmkh.basekmpproject.storage.database

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase

actual class DatabaseContext(context: Context) {
    internal val appContext: Context = context.applicationContext
}

// `synchronous()` adapts the asynchronously generated schema back to the blocking one the Android
// driver expects. `generateAsync` describes the query API, not the platform: Android's SQLite is
// still a synchronous library, and the driver opens and migrates the file on its own.
internal actual suspend fun createDriver(context: DatabaseContext): SqlDriver =
    AndroidSqliteDriver(AppDatabase.Schema.synchronous(), context.appContext, DatabaseName)
