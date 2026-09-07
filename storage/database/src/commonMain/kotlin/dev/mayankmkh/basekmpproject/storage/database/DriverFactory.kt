package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

/**
 * Opens a driver with the schema in place.
 *
 * The Web Worker driver and fresh desktop schema creation are suspending operations.
 * `AppDatabaseDriverProvider` keeps callers from paying that cost more than once.
 */
internal expect suspend fun createDriver(context: PlatformContext): SqlDriver

internal const val DatabaseName = "app.db"

/**
 * Builds the driver and resolves the connection behind it, so a driver only ever leaves
 * `AppDatabaseDriverProvider` fully open.
 *
 * `AndroidSqliteDriver` connects on its first statement rather than in its constructor, from a
 * `lazy` whose monitor is re-entrant for the calling thread. On a clean install that first
 * statement runs `onCreate`, and the `synchronous()` schema adapter drives the asynchronously
 * generated schema through `runBlocking`, whose event loop drains the unconfined continuations
 * already pending on that thread -- one of the app's own query flows among them. That flow
 * re-enters the half-initialised `lazy`, opens the file a second time, and `ProcessLock` fails the
 * launch with `OverlappingFileLockException`. Connecting here, under the provider's mutex and
 * before any capability database exists, means no query can reach a driver that is still opening.
 */
internal suspend fun createOpenDriver(context: PlatformContext): SqlDriver {
    val driver = createDriver(context)
    var opened = false
    try {
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT 1",
                mapper = { QueryResult.Unit },
                parameters = 0,
            )
            .await()
        opened = true
        return driver
    } finally {
        if (!opened) driver.close()
    }
}
