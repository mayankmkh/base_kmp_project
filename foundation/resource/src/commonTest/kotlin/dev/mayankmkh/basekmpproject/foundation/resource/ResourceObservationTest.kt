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
    fun `initial is unsynchronized, has no value and is loading its first one`() {
        val observation = ResourceObservation.initial<String>()

        assertFalse(observation.hasValue)
        assertEquals(ResourceOperation.Unsynchronized, observation.operation)
        assertFalse(observation.isRefreshing)
        assertTrue(observation.isInitialLoading)
        assertFalse(observation.isAbsent)
        assertNull(observation.failure)
    }

    @Test
    fun `a durable value carried into an unsynchronized process is not initial loading`() {
        val observation =
            ResourceObservation(
                value = "durable",
                operation = ResourceOperation.Unsynchronized,
            )

        assertTrue(observation.hasValue)
        assertFalse(observation.isInitialLoading)
        assertFalse(observation.isAbsent)
    }

    @Test
    fun `a refresh without a value is still initial loading`() {
        val observation =
            ResourceObservation<String>(
                value = null,
                operation = ResourceOperation.Refreshing,
            )

        assertTrue(observation.isInitialLoading)
    }

    @Test
    fun `null value with idle operation is confirmed absent`() {
        val observation =
            ResourceObservation<String>(
                value = null,
                operation = ResourceOperation.Idle,
            )

        assertTrue(observation.isAbsent)
        assertFalse(observation.isInitialLoading)
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
        assertFalse(observation.isInitialLoading)
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
    fun `status mapping prefers in-flight then failure then a key that never succeeded`() {
        val problem = Problem(ProblemKind.UNEXPECTED)
        val failed = SyncStatus(inFlight = false, lastFailure = problem, hasSucceeded = true)

        assertEquals(ResourceOperation.Refreshing, failed.copy(inFlight = true).toOperation())
        assertEquals(ResourceOperation.Failed(problem), failed.toOperation())
        assertEquals(
            ResourceOperation.Unsynchronized,
            failed.copy(lastFailure = null, hasSucceeded = false).toOperation(),
        )
        assertEquals(ResourceOperation.Idle, failed.copy(lastFailure = null).toOperation())
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
