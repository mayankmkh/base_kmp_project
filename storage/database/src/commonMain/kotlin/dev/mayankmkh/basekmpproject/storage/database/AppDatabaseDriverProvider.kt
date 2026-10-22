package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
@OptIn(ExperimentalAtomicApi::class)
public class AppDatabaseDriverProvider(
    context: PlatformContext,
    dispatchers: AppDispatchers,
) : SqlDriverProvider, AutoCloseable {
    // Koin's compiler plugin builds the primary constructor, so the test seam is the secondary one.
    private var openDriver: suspend () -> SqlDriver = { createOpenDriver(context) }

    internal constructor(
        context: PlatformContext,
        dispatchers: AppDispatchers,
        openDriver: suspend () -> SqlDriver,
    ) : this(context, dispatchers) {
        this.openDriver = openDriver
    }

    override val dispatcher: CoroutineDispatcher = dispatchers.disk

    private val mutex = Mutex()

    // One atomic state rather than a driver field and a closed flag: `close` cannot take the
    // mutex, so an open finishing after `close` must lose a compare-and-set, not miss a flag.
    private val state = AtomicReference<State>(State.Unopened)

    override suspend fun driver(): SqlDriver {
        when (val current = state.load()) {
            is State.Open -> return current.driver
            State.Closed -> error(ClosedMessage)
            State.Unopened -> Unit
        }
        return withContext(dispatcher) {
            mutex.withLock {
                when (val current = state.load()) {
                    is State.Open -> current.driver
                    State.Closed -> error(ClosedMessage)
                    State.Unopened -> publish(openDriver())
                }
            }
        }
    }

    private fun publish(driver: SqlDriver): SqlDriver {
        if (state.compareAndSet(State.Unopened, State.Open(driver))) return driver
        // `close` ran while this driver was opening, so no one else will ever close it.
        driver.close()
        error(ClosedMessage)
    }

    override fun close() {
        (state.exchange(State.Closed) as? State.Open)?.driver?.close()
    }

    private sealed interface State {
        data object Unopened : State

        class Open(val driver: SqlDriver) : State

        data object Closed : State
    }
}

private const val ClosedMessage = "The application database driver provider is closed"
