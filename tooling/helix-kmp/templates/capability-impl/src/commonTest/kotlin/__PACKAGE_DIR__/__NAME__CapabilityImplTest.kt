package __PACKAGE__

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import __API_PACKAGE__.Create__NAME__Result
import __API_PACKAGE__.__NAME__Field
import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import dev.mayankmkh.basekmpproject.foundation.resource.isAbsent
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private class Fake__NAME__RemoteSource : __NAME__RemoteSource {
    var result: Result<List<__NAME__Record>, NetworkFailure> = Ok(RECORDS)
    var createResult: Result<Create__NAME__RemoteAnswer, NetworkFailure> =
        Ok(Create__NAME__RemoteAnswer.Created(RECORDS.first()))
    var gate: CompletableDeferred<Unit>? = null
    var fetchCount: Int = 0
        private set

    override suspend fun fetchAll(): Result<List<__NAME__Record>, NetworkFailure> {
        fetchCount++
        gate?.await()
        return result
    }

    override suspend fun create(label: String): Result<Create__NAME__RemoteAnswer, NetworkFailure> =
        createResult
}

private val RECORDS = listOf(__NAME__Record(__NAME__Id("1"), "__name__ 1"))
private val UNEXPECTED =
    NetworkFailure.Unexpected("request-id", IllegalStateException("unexpected"))

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
                assertEquals(ResourceOperation.Refreshing, loading.operation)

                remote.gate?.complete(Unit)
                val synchronized = awaitItem()
                assertEquals(RECORDS, synchronized.value)
                assertEquals(ResourceOperation.Idle, synchronized.operation)
                assertEquals(1, remote.fetchCount)
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `a failed synchronization keeps the observation valueless until retry`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            remote.gate = CompletableDeferred()
            remote.result = Err(UNEXPECTED)
            val capability = capability(remote)

            capability.observeAll().test {
                assertEquals(ResourceOperation.Refreshing, awaitItem().operation)

                remote.gate?.complete(Unit)
                val failed = awaitItem()
                assertNull(failed.value)
                assertEquals(
                    ProblemKind.UNEXPECTED,
                    assertIs<ResourceOperation.Failed>(failed.operation).problem.kind,
                )

                remote.result = Ok(RECORDS)
                assertEquals(Outcome.Completed(Unit), capability.refresh())
                val recovered = expectMostRecentItem()
                assertEquals(RECORDS, recovered.value)
                assertEquals(ResourceOperation.Idle, recovered.operation)
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `a missing item after synchronization is confirmed absent`() =
        runTest(dispatcher) {
            val capability = capability(Fake__NAME__RemoteSource())

            capability.observe(__NAME__Id("missing")).test {
                assertEquals(true, awaitItem().isAbsent)
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `a mutation refusal completes inside the command result`() =
        runTest(dispatcher) {
            val remote = Fake__NAME__RemoteSource()
            remote.createResult =
                Ok(
                    Create__NAME__RemoteAnswer.InvalidInput(
                        listOf(Violation(__NAME__Field.LABEL, "blank"))
                    )
                )
            val capability = capability(remote)

            val outcome = capability.create("")

            val completed = assertIs<Outcome.Completed<Create__NAME__Result>>(outcome)
            assertIs<Create__NAME__Result.InvalidInput>(completed.value)
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
            logger = Logger,
        )
    }
}
