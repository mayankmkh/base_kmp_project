package dev.mayankmkh.basekmpproject.capability.identity.impl

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.SessionState
import dev.mayankmkh.basekmpproject.foundation.network.CredentialRefreshResult
import dev.mayankmkh.basekmpproject.foundation.preferences.inMemoryPreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class IdentityCapabilityImplTest {
    @Test
    fun `session starts anonymous`() = runTest {
        capability().observeSession().test {
            assertEquals(SessionState.Anonymous, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sign in publishes signed in`() = runTest {
        val capability = capability()

        capability.observeSession().test {
            assertEquals(SessionState.Anonymous, awaitItem())
            capability.signIn(AuthToken("access"))
            assertEquals(SessionState.SignedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sign out publishes anonymous`() = runTest {
        val capability = capability()
        capability.signIn(AuthToken("access"))

        capability.observeSession().test {
            assertEquals(SessionState.SignedIn, awaitItem())
            capability.signOut()
            assertEquals(SessionState.Anonymous, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejected refresh clears credential and session`() = runTest {
        val capability = capability()
        capability.signIn(AuthToken("access"))

        capability.observeSession().test {
            assertEquals(SessionState.SignedIn, awaitItem())
            assertEquals(CredentialRefreshResult.Rejected, capability.refreshBearerToken())
            assertEquals(SessionState.Anonymous, awaitItem())
            assertNull(capability.currentBearerToken())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `credential provider reflects the credential store`() = runTest {
        val capability = capability()

        assertNull(capability.currentBearerToken())
        capability.signIn(AuthToken("access"))
        assertEquals("access", capability.currentBearerToken())
    }

    private fun capability() = IdentityCapabilityImpl(CredentialStore(inMemoryPreferenceStore()))
}
