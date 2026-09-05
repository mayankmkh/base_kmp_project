package __PACKAGE__

import co.touchlab.kermit.Logger
import __API_PACKAGE__.Create__NAME__Result
import __API_PACKAGE__.__NAME__Commands
import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Queries
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.CommandBridge
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.SyncCoordinator
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.observations
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// The whole implementation is `internal`: only the Koin module is visible outside this module, so
// no consumer can reach past the API. The local source owns values, while `SyncCoordinator` owns
// only process-local work and status. Endpoint refusals become answer values before the bridge
// runs; one `CommandBridge` per Capability classifies and logs every generic network failure once,
// under this Capability's tag.
/** Implementation of the __name__ Capability. */
internal class __NAME__CapabilityImpl(
    private val remoteSource: __NAME__RemoteSource,
    private val localSource: __NAME__LocalSource,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
    logger: Logger,
) : __NAME__Queries, __NAME__Commands, AutoCloseable {
    private val scope = applicationRuntimeScope.childScope("__name__")
    private val bridge = CommandBridge(logger, "__name__")
    private val sync =
        SyncCoordinator<Unit>(
            scope,
            sync = { _, _ -> synchronize() },
            retryTriggers = connectivityMonitor.reconnects(),
            bridge = bridge,
        )

    override fun observeAll(): Flow<ResourceObservation<List<__NAME__Record>>> =
        sync.observations(
            Unit,
            combine(localSource.observeAll(), localSource.observeSynchronized()) {
                records,
                synchronized ->
                records.takeIf { synchronized }
            },
        )

    override fun observe(id: __NAME__Id): Flow<ResourceObservation<__NAME__Record>> =
        sync.observations(Unit, localSource.observe(id))

    override suspend fun refresh(qos: RefreshQos): Outcome<Unit> = sync.sync(Unit, qos)

    override suspend fun create(label: String): Outcome<Create__NAME__Result> =
        bridge.toOutcome(remoteSource.create(label), "create") { answer ->
            when (answer) {
                is Create__NAME__RemoteAnswer.Created -> {
                    localSource.upsert(answer.record)
                    Create__NAME__Result.Created(answer.record.id)
                }
                is Create__NAME__RemoteAnswer.InvalidInput ->
                    Create__NAME__Result.InvalidInput(answer.violations)
            }
        }

    override fun close() {
        scope.cancel()
    }

    private suspend fun synchronize(): Outcome<Unit> =
        bridge.commit(remoteSource.fetchAll(), "refresh", localSource::replaceAll)
}
