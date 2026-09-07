package dev.mayankmkh.basekmpproject.app.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * The id boundary is the only untyped step in environment selection: four build systems can carry
 * the environment as text and nothing else, so this is where a typo in a flavor name, an xcconfig
 * or a meta tag has to stop.
 */
class BuildEnvironmentTest {
    @Test
    fun everyEnvironmentRoundTripsThroughItsId() {
        BuildEnvironment.entries.forEach { assertEquals(it, BuildEnvironment.fromId(it.id)) }
    }

    @Test
    fun anUnknownIdFailsRatherThanDefaulting() {
        // "production" and "STAGING" are the near misses a hand-edited xcconfig or flavor produces;
        // silently falling back to production for any of these is the failure this guards.
        listOf("", " ", "production", "PROD", "STAGING", "demo", "dev").forEach { id ->
            assertFailsWith<IllegalStateException>(id) { BuildEnvironment.fromId(id) }
        }
    }

    @Test
    fun stagingIsStorageIsolatedAndProductionKeepsTheIdItShippedWith() {
        assertEquals("dev.mayankmkh.basekmpproject", BuildEnvironment.Production.applicationId)
        assertEquals(
            "dev.mayankmkh.basekmpproject.staging",
            BuildEnvironment.Staging.applicationId,
        )
        assertNotEquals(
            BuildEnvironment.Production.applicationId,
            BuildEnvironment.Staging.applicationId,
        )
    }
}
