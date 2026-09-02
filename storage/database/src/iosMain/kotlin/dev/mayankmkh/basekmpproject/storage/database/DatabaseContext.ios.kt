package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.mayankmkh.basekmpproject.storage.database.generated.PostsDatabase

actual class DatabaseContext

// The native driver resolves its own location under the app's Documents directory and, like the
// Android one, creates and migrates the file itself -- hence the `synchronous()` schema.
internal actual suspend fun createDriver(context: DatabaseContext): SqlDriver =
    NativeSqliteDriver(PostsDatabase.Schema.synchronous(), DatabaseName)
