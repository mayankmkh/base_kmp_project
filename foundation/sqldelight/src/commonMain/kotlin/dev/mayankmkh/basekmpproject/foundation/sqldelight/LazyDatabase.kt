package dev.mayankmkh.basekmpproject.foundation.sqldelight

import app.cash.sqldelight.db.SqlDriver
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Hands out the open, migrated driver of the application database, and the lane its statements run
 * on. Implemented by `:storage:database`.
 */
public interface SqlDriverProvider {
    /**
     * The dispatcher every statement against [driver] belongs on.
     *
     * SQLite is blocking file I/O wherever the database is a file, so the dispatcher a caller
     * happens to be on -- a Compose collector's `Dispatchers.Main.immediate`, typically -- is never
     * the right one. The database is one resource with one owner, so the owner names the lane
     * instead of every local source being handed a dispatcher of its own.
     */
    public val dispatcher: CoroutineDispatcher

    public suspend fun driver(): SqlDriver
}

/**
 * Builds a capability's generated database over the shared driver once, on first use, and keeps
 * every statement against it on the driver owner's lane.
 *
 * A suspending factory cannot be a Koin `single`; this keeps the memoisation out of every local
 * source. The generated database is never handed out, only lent to [use] and [observe], so no
 * caller can end up running SQL on whichever dispatcher it was invoked from.
 */
public class LazyDatabase<D : Any>(
    private val drivers: SqlDriverProvider,
    private val create: (SqlDriver) -> D,
) {
    private val mutex = Mutex()

    @Volatile private var database: D? = null

    /** Runs [block] against the memoised database, opening it on the first call. */
    public suspend fun <R> use(block: suspend D.() -> R): R =
        withContext(drivers.dispatcher) { get().block() }

    /**
     * Observes a query over the memoised database, opening it on first collection. Building the
     * flow never suspends, so a coordinator-backed observation stays cold until somebody collects
     * it. The database is obtained once, so rows flow straight to the collector with no
     * intermediate channel beyond the one [kotlinx.coroutines.flow.flowOn] uses to leave the
     * collector's lane.
     */
    public fun <T> observe(query: D.() -> Flow<T>): Flow<T> =
        flow { emitAll(get().query()) }.flowOn(drivers.dispatcher)

    private suspend fun get(): D =
        database
            ?: mutex.withLock {
                database ?: create(drivers.driver()).also { database = it }
            }
}
