package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Opens the application database once, on first use.
 *
 * A suspending factory cannot be a Koin `single { }`, and opening a SQLite file eagerly at startup
 * would put disk I/O on the path that builds the object graph. So the singleton is this provider,
 * and the driver it hands out is created on whichever coroutine asks for it first -- but always on
 * [dispatcher], never on the caller's own lane.
 */
public class AppDatabaseDriverProvider(
    private val context: PlatformContext,
    dispatchers: AppDispatchers,
) : SqlDriverProvider, AutoCloseable {
    override val dispatcher: CoroutineDispatcher = dispatchers.disk

    private val mutex = Mutex()

    // `@Volatile` makes the fast path a safe publication rather than a data race.
    @Volatile private var sharedDriver: SqlDriver? = null
    @Volatile private var closed: Boolean = false

    override suspend fun driver(): SqlDriver {
        check(!closed) { "The application database driver provider is closed" }
        return sharedDriver
            ?: withContext(dispatcher) {
                mutex.withLock {
                    check(!closed) { "The application database driver provider is closed" }
                    sharedDriver ?: createOpenDriver(context).also { sharedDriver = it }
                }
            }
    }

    override fun close() {
        if (closed) return
        closed = true
        val driver = sharedDriver
        sharedDriver = null
        driver?.close()
    }
}
