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
    fun initialIsUnknownRefreshingAndHasNoValue() {
        val observation = ResourceObservation.initial<String>()

        assertFalse(observation.hasValue)
        assertTrue(observation.isRefreshing)
        assertEquals(ResourceFreshness.UNKNOWN, observation.freshness)
        assertNull(observation.failure)
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
}
