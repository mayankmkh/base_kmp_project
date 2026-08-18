package dev.mayankmkh.basekmpproject.shared.libs.prefs

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.shared.libs.prefs.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CredentialsPreferencesTest {

    private val preferences = CredentialsPreferences(InMemoryPreferencesDataStore())

    @Test
    fun `there is no token to begin with`() = runTest {
        assertNull(preferences.getAuthToken())
        assertFalse(preferences.hasAuthToken())
    }

    @Test
    fun `a stored token reads back`() = runTest {
        preferences.setAuthToken("token")

        assertEquals("token", preferences.getAuthToken())
        assertTrue(preferences.hasAuthToken())
    }

    @Test
    fun `signing out leaves no token behind`() = runTest {
        preferences.setAuthToken("token")

        preferences.removeAuthToken()

        assertNull(preferences.getAuthToken())
        assertFalse(preferences.hasAuthToken())
    }

    @Test
    fun `the flow follows the token through its whole life`() = runTest {
        preferences.getAuthTokenFlowable().test {
            assertNull(awaitItem())

            preferences.setAuthToken("first")
            assertEquals("first", awaitItem())

            preferences.setAuthToken("second")
            assertEquals("second", awaitItem())

            preferences.removeAuthToken()
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
