package dev.mayankmkh.basekmpproject.foundation.resource

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `null value with idle operation is confirmed absent`() {
        val observation =
            ResourceObservation<String>(
                value = null,
                operation = ResourceOperation.Idle,
            )

        assertTrue(observation.isAbsent)
    }

    @Test
    fun `failure without a value is legal and exposes its problem`() {
        val problem = Problem(ProblemKind.OFFLINE)
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
        val problem = Problem(ProblemKind.OFFLINE)
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
        val problem = Problem(ProblemKind.UNEXPECTED)
        val failed = SyncStatus(inFlight = false, lastFailure = problem, hasSucceeded = true)

        assertEquals(
            ResourceOperation.Refreshing,
            failed.copy(inFlight = true).toOperation(hasValue = true),
        )
        assertEquals(ResourceOperation.Failed(problem), failed.toOperation(hasValue = true))
        assertEquals(
            ResourceOperation.Refreshing,
            failed.copy(lastFailure = null, hasSucceeded = false).toOperation(hasValue = false),
        )
        assertEquals(
            ResourceOperation.Idle,
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

    @Test
    fun `problem derives retry and ambiguity from its kind`() {
        assertTrue(Problem(ProblemKind.OFFLINE).retryable)
        assertTrue(Problem(ProblemKind.TIMEOUT).retryable)
        assertTrue(Problem(ProblemKind.SERVER).retryable)
        assertFalse(Problem(ProblemKind.FORBIDDEN).retryable)
        assertFalse(Problem(ProblemKind.UNEXPECTED).retryable)
        assertTrue(Problem(ProblemKind.TIMEOUT).mayHaveApplied)
        assertFalse(Problem(ProblemKind.SERVER).mayHaveApplied)
    }
}
