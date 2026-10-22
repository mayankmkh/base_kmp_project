package dev.mayankmkh.basekmpproject.app.shared.di

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import dev.mayankmkh.basekmpproject.foundation.preferences.inMemoryPreferenceStores
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import io.ktor.client.engine.HttpClientEngine
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * The surfaces a test swaps and nothing else: the two stored-data factories, which production code
 * may build at most once per process, the application database, and the HTTP engine, which would
 * otherwise reach the network. Everything else stays the graph `initKoin` starts.
 *
 * The database is the real merged schema in a file of its own under the temporary directory, so no
 * test reaches the per-user data directory, which off macOS follows `APPDATA` or `XDG_CONFIG_HOME`
 * rather than `user.home`.
 *
 * A null [engine] or [dispatchers] leaves that production definition in place. The graph-close test
 * needs the real engine, while UI tests inject their controlled dispatcher so no coroutine escapes
 * the test lifecycle.
 */
internal fun processSurfaceOverrides(
    engine: HttpClientEngine?,
    dispatchers: AppDispatchers? = null,
): Module = module {
    single<PreferenceStores> { inMemoryPreferenceStores() }
    single<SecretStores> { inMemorySecretStores() }
    single<SqlDriverProvider> {
        TemporaryDatabaseDriverProvider(get<AppDispatchers>().disk)
    } onClose
        {
            (it as? TemporaryDatabaseDriverProvider)?.close()
        }
    if (engine != null) {
        single<HttpClientEngine> { engine }
    }
    if (dispatchers != null) {
        single<AppDispatchers> { dispatchers }
    }
}

/**
 * The application database in a temporary file, opened on first use and deleted with its directory
 * when the graph closes. Like the production provider it refuses to open again once closed, and it
 * keeps that provider's state shape: `close` cannot take the mutex, so an open finishing after
 * `close` loses a compare-and-set and closes its own driver rather than leaking it.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class TemporaryDatabaseDriverProvider(override val dispatcher: CoroutineDispatcher) :
    SqlDriverProvider, AutoCloseable {
    private val directory: File = Files.createTempDirectory("base-kmp-app-database").toFile()
    val file: File = directory.resolve("app.db")

    private val mutex = Mutex()
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
                    State.Unopened ->
                        publish(
                            JdbcSqliteDriver(
                                url = "jdbc:sqlite:${file.absolutePath}",
                                schema = AppDatabase.Schema.synchronous(),
                            )
                        )
                }
            }
        }
    }

    private fun publish(driver: SqlDriver): SqlDriver {
        if (state.compareAndSet(State.Unopened, State.Open(driver))) return driver
        // `close` ran while this driver was opening, so no one else will ever close it, and the
        // directory `close` already deleted may hold the file this open created.
        driver.close()
        directory.deleteRecursively()
        error(ClosedMessage)
    }

    override fun close() {
        (state.exchange(State.Closed) as? State.Open)?.driver?.close()
        directory.deleteRecursively()
    }

    private sealed interface State {
        data object Unopened : State

        class Open(val driver: SqlDriver) : State

        data object Closed : State
    }
}

private const val ClosedMessage = "The temporary application database is closed"
