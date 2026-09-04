package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ObservationsTest {
    @Test
    fun `observations combine the durable value with the key status and drop duplicates`() =
        runTest {
            val problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
            val values = MutableStateFlow<String?>(null)
            val release = CompletableDeferred<RefreshOutcome>()
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

                release.complete(RefreshOutcome.Failed(problem))
                assertEquals(
                    ResourceObservation("cached", ResourceOperation.Failed(problem)),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
