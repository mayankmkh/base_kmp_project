package dev.mayankmkh.basekmpproject.capability.identity.impl

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.SessionState
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import dev.mayankmkh.basekmpproject.foundation.network.CredentialRefreshResult
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStoreException
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class CredentialStoreProviderTest {
    private val credentials = CredentialStore(inMemorySecretStores(), Logger)
    private val identity = IdentityCapabilityImpl(credentials)
    private val provider = provider(credentials)

    @Test
    fun `the provider hands the client whatever the store holds`() = runTest {
        assertNull(provider.currentCredential())

        identity.signIn(AuthToken("access"))

        assertEquals("access", provider.currentCredential())
    }

    /** No refresh endpoint exists, so a rejected credential is a finished session. */
    @Test
    fun `a rejected credential ends the session`() = runTest {
        identity.signIn(AuthToken("access"))

        identity.observeSession().test {
            assertEquals(SessionState.SignedIn, awaitItem())
            assertEquals(CredentialRefreshResult.Rejected, provider.refreshCredential("access"))
            assertEquals(SessionState.Anonymous, awaitItem())
            assertNull(provider.currentCredential())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A late 401 for the credential from before a fresh sign-in must not end the new session. */
    @Test
    fun `a rejection of an older credential leaves the current session alone`() = runTest {
        identity.signIn(AuthToken("second"))

        assertEquals(CredentialRefreshResult.Rejected, provider.refreshCredential("first"))

        assertEquals("second", provider.currentCredential())
    }

    /** A request that carried no credential says nothing about the session stored now. */
    @Test
    fun `a rejection without a credential leaves the session alone`() = runTest {
        identity.signIn(AuthToken("access"))

        assertEquals(CredentialRefreshResult.Rejected, provider.refreshCredential(null))

        assertEquals("access", provider.currentCredential())
    }

    /**
     * A store that cannot be read sends requests without a credential instead of failing them, and
     * clears nothing: the credential is back, and the session reads as signed in, once it recovers.
     * A 401 for a request sent without one clears nothing either; one for a credential cannot be
     * checked against the store, so it throws, which the client treats as `Unavailable`.
     */
    @Test
    fun `an unreadable credential store reads as no credential until it recovers`() = runTest {
        val stores = OutageSecretStores()
        val credentials = CredentialStore(stores, Logger)
        val identity = IdentityCapabilityImpl(credentials)
        val provider = provider(credentials)
        identity.signIn(AuthToken("access"))
        stores.down = true

        assertNull(provider.currentCredential())
        assertEquals(CredentialRefreshResult.Rejected, provider.refreshCredential(null))
        assertFailsWith<SecretStoreException> { provider.refreshCredential("access") }

        identity.observeSession().test {
            expectNoEvents()
            stores.down = false
            assertEquals(SessionState.SignedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("access", provider.currentCredential())
    }
}

internal fun provider(credentials: CredentialStore): CredentialProvider =
    CredentialStoreProvider(credentials)
