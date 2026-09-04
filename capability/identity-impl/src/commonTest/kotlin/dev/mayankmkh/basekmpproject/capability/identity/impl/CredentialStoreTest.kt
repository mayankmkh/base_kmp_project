package dev.mayankmkh.basekmpproject.capability.identity.impl

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CredentialStoreTest {

    private val credentials = CredentialStore(inMemorySecretStore())

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
}
