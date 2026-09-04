package dev.mayankmkh.basekmpproject.storage.database

import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Supplies the fully assembled application database.
 *
 * An interface rather than the concrete provider lets a test supply an in-memory database without a
 * [PlatformContext] and without touching the user's home directory.
 */
fun interface AppDatabaseProvider {
    suspend fun database(): AppDatabase
}

/**
 * Opens the database once, on first use.
 *
 * A suspending factory cannot be a Koin `single { }`, and opening a SQLite file eagerly at startup
 * would put disk I/O on the path that builds the object graph. So the singleton is this provider,
 * and the database it hands out is created on whichever coroutine asks for it first.
 */
class DefaultAppDatabaseProvider(private val context: PlatformContext) : AppDatabaseProvider {
    private val mutex = Mutex()

    // `@Volatile` so the fast path below is a safe publication rather than a data race: without it
    // a thread could observe a non-null reference to a partly constructed database.
    @Volatile private var database: AppDatabase? = null

    override suspend fun database(): AppDatabase =
        database
            ?: mutex.withLock {
                // Re-checked inside the lock: several coroutines can pass the null test above
                // before any of them acquires it.
                database ?: AppDatabase(createDriver(context)).also { database = it }
            }
}
