package __PACKAGE__

import app.cash.turbine.test
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

// The remote source is the only seam a Capability test needs to fake; the local source and the
// coordinator are the real ones. `gate` holds a fetch open so a test can observe the in-flight
// state before the result lands.
private class Fake__NAME__RemoteSource : __NAME__RemoteSource {
    var result: Result<List<__NAME__Record>, ResourceProblem> = Ok(RECORDS)
    var gate: CompletableDeferred<Unit>? = null
    var fetchCount: Int = 0
        private set

    override suspend fun fetchAll(): Result<List<__NAME__Record>, ResourceProblem> {
        fetchCount++
        gate?.await()
        return result
    }
}

private val RECORDS = listOf(__NAME__Record(__NAME__Id("1"), "__name__ 1"))
private val TEMPORARY = ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)

class __NAME__CapabilityImplTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `the first observer starts one synchronization`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            remote.gate = CompletableDeferred()
            val capability = capability(remote)

            capability.observeAll().test {
                val loading = awaitItem()
                assertNull(loading.value)
                assertSame(ResourceOperation.Refreshing, loading.operation)

                remote.gate?.complete(Unit)
                val synchronized = awaitItem()
                assertEquals(RECORDS, synchronized.value)
                assertSame(ResourceOperation.Idle, synchronized.operation)
                assertEquals(1, remote.fetchCount)
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `a failed synchronization keeps the observation valueless until a retry succeeds`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            remote.gate = CompletableDeferred()
            remote.result = Err(TEMPORARY)
            val capability = capability(remote)

            capability.observeAll().test {
                assertSame(ResourceOperation.Refreshing, awaitItem().operation)

                remote.gate?.complete(Unit)
                val failed = awaitItem()
                assertNull(failed.value)
                val problem = assertIs<ResourceOperation.Failed>(failed.operation).problem
                assertEquals(ResourceProblemCategory.TEMPORARY, problem.category)

                remote.result = Ok(RECORDS)
                assertSame(RefreshOutcome.Succeeded, capability.refresh())
                // The rows land while the retry is still in flight, so only the final item matters.
                val recovered = expectMostRecentItem()
                assertEquals(RECORDS, recovered.value)
                assertSame(ResourceOperation.Idle, recovered.operation)
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `a record missing after a successful synchronization remains refreshing`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            val capability = capability(remote)

            capability.observe(__NAME__Id("missing")).test {
                val observation = awaitItem()
                assertNull(observation.value)
                assertSame(ResourceOperation.Refreshing, observation.operation)
                assertEquals(1, remote.fetchCount)
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `re-observing within the interval does not synchronize again`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            val capability = capability(remote)

            repeat(3) {
                val observer = backgroundScope.launch { capability.observeAll().collect() }
                observer.cancelAndJoin()
            }

            assertEquals(1, remote.fetchCount)
            capability.close()
        }

    // Only an observed key whose last attempt failed `OFFLINE` is retried on reconnect. The
    // placeholder mapping never reports offline, so this scaffold can only show the negative side;
    // `SyncCoordinatorTest` and `PostsCapabilityImplTest` cover the retry itself.
    @Test
    fun `reconnect leaves a synchronized collection alone`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            val online = MutableStateFlow(false)
            val capability = capability(remote, ConnectivityMonitor { online })
            val observer = backgroundScope.launch { capability.observeAll().collect() }
            assertEquals(1, remote.fetchCount)

            online.value = true

            assertEquals(1, remote.fetchCount)
            observer.cancelAndJoin()
            capability.close()
        }

    private fun capability(
        remote: __NAME__RemoteSource,
        connectivity: ConnectivityMonitor = ConnectivityMonitor { MutableStateFlow(true) },
    ): __NAME__CapabilityImpl {
        val handler = CoroutineExceptionHandler { _, throwable -> throw throwable }
        return __NAME__CapabilityImpl(
            remoteSource = remote,
            localSource = __NAME__LocalSource(),
            applicationRuntimeScope = ApplicationRuntimeScope(dispatcher, handler),
            connectivityMonitor = connectivity,
        )
    }
}
