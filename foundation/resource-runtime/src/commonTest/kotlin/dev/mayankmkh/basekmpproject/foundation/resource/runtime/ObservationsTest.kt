package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ObservationsTest {
    @Test
    fun `observations combine the durable value with the key status and drop duplicates`() =
        runTest {
            val problem = Problem(ProblemKind.OFFLINE)
            val values = MutableStateFlow<String?>(null)
            val release = CompletableDeferred<Outcome<Unit>>()
            val coordinator =
                SyncCoordinator<String>(scope = backgroundScope, sync = { _, _ -> release.await() })

            coordinator.observations("key", values).test {
                assertEquals(ResourceObservation.initial(), awaitItem())
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
}
