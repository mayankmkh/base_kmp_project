package __PACKAGE__

import com.github.michaelbull.result.fold
import __API_PACKAGE__.__NAME__Commands
import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Queries
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.SyncCoordinator
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.observations
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// The whole implementation is `internal`: only the Koin module is visible outside this module, so
// no consumer can reach past the API.
//
// The local source owns the value. `SyncCoordinator` from `:foundation:resource-runtime` owns the
// process-local sync ledger: one worker per key, one appearance sync per new observer (skipped
// while the last attempt is younger than its interval), a retry of observed keys whose last attempt
// failed offline whenever `retryTriggers` emits, and the `status` behind every observation.
// `observations` wraps a durable value flow in `observing` and applies the contract's
// `SyncStatus.toOperation` mapping, so every Query goes through it and the appearance sync and the
// offline retry know about it. The app shares the connectivity monitor once, so each coordinator
// simply passes `connectivityMonitor.reconnects()`. `:capability:posts-impl` is the worked example
// with SQLDelight, Ktor, and a second keyed coordinator.
/** Implementation of the __name__ Capability. */
internal class __NAME__CapabilityImpl(
    private val remoteSource: __NAME__RemoteSource,
    private val localSource: __NAME__LocalSource,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
) : __NAME__Queries, __NAME__Commands, AutoCloseable {
    // Shared resource work lives in a named child of the application runtime scope, never in a
    // ViewModel scope. `close` ends it together with the Koin single.
    private val scope = applicationRuntimeScope.childScope("__name__")
    private val sync =
        SyncCoordinator<Unit>(
            scope,
            sync = { _, _ -> synchronize() },
            retryTriggers = connectivityMonitor.reconnects(),
        )

    override fun observeAll(): Flow<ResourceObservation<List<__NAME__Record>>> =
        sync.observations(
            Unit,
            combine(localSource.observeAll(), localSource.observeSynchronized()) {
                records,
                synchronized ->
                // Without the marker an empty collection has no value yet.
                records.takeIf { synchronized }
            },
        )

    override fun observe(id: __NAME__Id): Flow<ResourceObservation<__NAME__Record>> =
        sync.observations(Unit, localSource.observe(id))

    override suspend fun refresh(qos: RefreshQos): RefreshOutcome = sync.sync(Unit, qos)

    override fun close() {
        scope.cancel()
    }

    // One sync fetches, converts, and commits the rows plus the synchronized marker in a single
    // local write. The coordinator records the outcome; nothing else remembers it.
    private suspend fun synchronize(): RefreshOutcome {
        val fetched = remoteSource.fetchAll()
        return fetched.fold(
            success = { records ->
                localSource.replaceAll(records)
                RefreshOutcome.Succeeded
            },
            failure = { RefreshOutcome.Failed(it) },
        )
    }
}

// A network-backed source returns `Result<T, NetworkFailure>`, and its sync function is one call to
// `commit { localSource.write(it) }` from `:foundation:resource-runtime`; `commit` maps failures
// with `toResourceProblem()`. Only an `OFFLINE` failure is retried when connectivity returns, so a
// real mapping must report offline transport failures as such.
