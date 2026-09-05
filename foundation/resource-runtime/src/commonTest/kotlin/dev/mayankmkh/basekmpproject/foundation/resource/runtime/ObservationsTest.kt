package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ObservationsTest {
    @Test
    fun `observations combine the durable value with the key status and drop duplicates`() =
        runTest {
            val problem = Problem(ProblemKind.OFFLINE)
            val values = MutableStateFlow<String?>(null)
            val release = CompletableDeferred<Outcome<Unit>>()
            val coordinator =
                SyncCoordinator<String>(
                    scope = backgroundScope,
                    sync = { _, _ -> release.await() },
                    bridge = CommandBridge(Logger, "test"),
                )

            coordinator.observations("key", values).test {
                assertEquals(RefreshingWithoutValue, awaitItem())
                values.value = "cached"
                assertEquals(
                    ResourceObservation("cached", ResourceOperation.Refreshing),
                    awaitItem(),
                )
                values.value = "cached"
                expectNoEvents()

                release.complete(Outcome.Failed(problem))
                assertEquals(
                    ResourceObservation("cached", ResourceOperation.Failed(problem)),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a key whose only worker was abandoned is reported unsynchronized`() = runTest {
        val workerScope = TestScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Outcome<Unit>>()
        val coordinator =
            SyncCoordinator<String>(
                scope = workerScope,
                sync = { _, _ ->
                    started.complete(Unit)
                    never.await()
                },
                bridge = CommandBridge(Logger, "test"),
            )

        coordinator.observations("key", MutableStateFlow<String?>(null)).test {
            assertEquals(RefreshingWithoutValue, awaitItem())
            started.await()
            workerScope.cancel()
            runCurrent()

            assertEquals(ResourceObservation.initial<String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val RefreshingWithoutValue =
            ResourceObservation<String>(value = null, operation = ResourceOperation.Refreshing)
    }
}
