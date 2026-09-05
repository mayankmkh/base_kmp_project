package dev.mayankmkh.basekmpproject.foundation.sqldelight

import app.cash.sqldelight.db.SqlDriver
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands out the open, migrated driver of the application database. Implemented by
 * `:storage:database`.
 */
public fun interface SqlDriverProvider {
    /** Returns the application's shared SQL driver. */
    public suspend fun driver(): SqlDriver
}

/**
 * Builds a capability's generated database over the shared driver once, on first use.
 *
 * A suspending factory cannot be a Koin `single`; this keeps the memoisation out of every local
 * source.
 */
public class LazyDatabase<D : Any>(
    private val drivers: SqlDriverProvider,
    private val create: (SqlDriver) -> D,
) {
    private val mutex = Mutex()

    @Volatile private var database: D? = null

    /** Returns the memoised generated database, creating it on the first call. */
    public suspend fun get(): D =
        database
            ?: mutex.withLock {
                database ?: create(drivers.driver()).also { database = it }
            }
}
