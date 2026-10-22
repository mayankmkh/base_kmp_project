package dev.mayankmkh.basekmpproject.capability.identity.impl

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class CredentialStoreTest {

    private val credentials = CredentialStore(inMemorySecretStores(), Logger)

    @Test
    fun `there is no token to begin with`() = runTest {
        assertNull(credentials.getAuthToken())
        assertFalse(credentials.hasAuthToken())
    }

    @Test
    fun `a stored token reads back`() = runTest {
        credentials.setAuthToken("token")

        assertEquals("token", credentials.getAuthToken())
        assertTrue(credentials.hasAuthToken())
    }

    @Test
    fun `signing out leaves no token behind`() = runTest {
        credentials.setAuthToken("token")

        credentials.removeAuthToken()

        assertNull(credentials.getAuthToken())
        assertFalse(credentials.hasAuthToken())
    }

    @Test
    fun `the flow follows the token through its whole life`() = runTest {
        credentials.observeAuthToken().test {
            assertNull(awaitItem())

            credentials.setAuthToken("first")
            assertEquals("first", awaitItem())

            credentials.setAuthToken("second")
            assertEquals("second", awaitItem())

            credentials.removeAuthToken()
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A store that cannot be read is waited out, not thrown into the collector: three failures in a
     * row pause 1 + 2 + 4 s, and the token the store kept arrives once it reads again.
     */
    @Test
    fun `the token flow waits out an unreadable store and recovers`() = runTest {
        val stores = OutageSecretStores()
        val outaged = CredentialStore(stores, Logger)
        outaged.setAuthToken("kept")
        stores.down = true

        val reads = mutableListOf<Pair<Long, String?>>()
        val job = launch {
            outaged.observeAuthToken().collect { reads += testScheduler.currentTime to it }
        }
        advanceTimeBy(6_500)
        runCurrent()
        assertEquals(emptyList<Pair<Long, String?>>(), reads)
        assertEquals(3, stores.failedObservations)

        stores.down = false
        advanceTimeBy(1_000)
        runCurrent()
        job.cancel()

        assertEquals(listOf<Pair<Long, String?>>(7_000L to "kept"), reads)
    }

    @Test
    fun `the retry pause doubles from one second and stops at thirty`() {
        assertEquals(
            listOf(1, 2, 4, 8, 16, 30, 30).map { it.seconds },
            (0..6).map(::unreadableRetryDelay),
        )
    }
}
