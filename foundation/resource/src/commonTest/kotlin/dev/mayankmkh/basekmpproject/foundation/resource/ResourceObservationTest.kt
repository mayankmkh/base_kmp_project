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
    fun initialIsUnknownRefreshingAndHasNoValue() {
        val observation = ResourceObservation.initial<String>()

        assertFalse(observation.hasValue)
        assertTrue(observation.isRefreshing)
        assertEquals(ResourceFreshness.UNKNOWN, observation.freshness)
        assertNull(observation.failure)
    }

    @Test
    fun `null value with fresh freshness is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ResourceObservation<String>(
                value = null,
                freshness = ResourceFreshness.FRESH,
                operation = ResourceOperation.Refreshing,
            )
        }
    }

    @Test
    fun `null value with idle operation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ResourceObservation<String>(
                value = null,
                freshness = ResourceFreshness.UNKNOWN,
                operation = ResourceOperation.Idle,
            )
        }
    }

    @Test
    fun `non-null value with unknown freshness is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ResourceObservation(
                value = "cached",
                freshness = ResourceFreshness.UNKNOWN,
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
                freshness = ResourceFreshness.UNKNOWN,
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
                freshness = ResourceFreshness.STALE,
                operation = ResourceOperation.Failed(problem),
            )

        assertTrue(observation.hasValue)
        assertFalse(observation.isRefreshing)
        assertEquals(problem, observation.failure)
    }

    @Test
    fun `failure is null for non-failed operations`() {
        val idle =
            ResourceObservation(
                value = "cached",
                freshness = ResourceFreshness.STALE,
                operation = ResourceOperation.Idle,
            )

        assertNull(idle.failure)
    }
}
