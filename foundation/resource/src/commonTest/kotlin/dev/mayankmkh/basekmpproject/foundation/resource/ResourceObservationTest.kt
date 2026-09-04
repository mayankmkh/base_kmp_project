package dev.mayankmkh.basekmpproject.foundation.resource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceObservationTest {
    @Test
    fun `refresh qos factories encode the standard visible and background policies`() {
        assertEquals(
            RefreshQos(RefreshPriority.VISIBLE, NetworkPreference.ANY_NETWORK),
            RefreshQos.visible(),
        )
        assertEquals(
            RefreshQos(RefreshPriority.BACKGROUND, NetworkPreference.ANY_NETWORK),
            RefreshQos.background(),
        )
    }

    @Test
    fun initialIsRefreshingAndHasNoValue() {
        val observation = ResourceObservation.initial<String>()

        assertFalse(observation.hasValue)
        assertTrue(observation.isRefreshing)
        assertNull(observation.failure)
    }

    @Test
    fun `null value with idle operation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ResourceObservation<String>(
                value = null,
                operation = ResourceOperation.Idle,
            )
        }
    }

    @Test
    fun `failure without a value is legal and exposes its problem`() {
        val problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
        val observation =
            ResourceObservation<String>(
                value = null,
                operation = ResourceOperation.Failed(problem),
            )

        assertFalse(observation.hasValue)
        assertEquals(problem, observation.failure)
    }

    @Test
    fun helpersExposeCachedValueAndFailure() {
        val problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
        val observation =
            ResourceObservation(
                value = "cached",
                operation = ResourceOperation.Failed(problem),
            )

        assertTrue(observation.hasValue)
        assertFalse(observation.isRefreshing)
        assertEquals(problem, observation.failure)
    }

    @Test
    fun `status mapping prefers in-flight then failure then a missing value`() {
        val problem = ResourceProblem(ResourceProblemCategory.PERMANENT, retryable = false)
        val failed = SyncStatus(inFlight = false, lastFailure = problem, hasSucceeded = true)

        assertEquals(
            ResourceOperation.Refreshing,
            failed.copy(inFlight = true).toOperation(hasValue = true),
        )
        assertEquals(ResourceOperation.Failed(problem), failed.toOperation(hasValue = true))
        assertEquals(
            ResourceOperation.Refreshing,
            failed.copy(lastFailure = null).toOperation(hasValue = false),
        )
        assertEquals(
            ResourceOperation.Idle,
            failed.copy(lastFailure = null).toOperation(hasValue = true),
        )
    }

    @Test
    fun `failure is null for non-failed operations`() {
        val idle =
            ResourceObservation(
                value = "cached",
                operation = ResourceOperation.Idle,
            )

        assertNull(idle.failure)
    }
}
