package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opens the application database once, on first use.
 *
 * A suspending factory cannot be a Koin `single { }`, and opening a SQLite file eagerly at startup
 * would put disk I/O on the path that builds the object graph. So the singleton is this provider,
 * and the driver it hands out is created on whichever coroutine asks for it first.
 */
public class AppDatabaseDriverProvider(private val context: PlatformContext) : SqlDriverProvider {
    private val mutex = Mutex()

    // `@Volatile` makes the fast path a safe publication rather than a data race.
    @Volatile private var sharedDriver: SqlDriver? = null

    override suspend fun driver(): SqlDriver =
        sharedDriver
            ?: mutex.withLock { sharedDriver ?: createDriver(context).also { sharedDriver = it } }
}
