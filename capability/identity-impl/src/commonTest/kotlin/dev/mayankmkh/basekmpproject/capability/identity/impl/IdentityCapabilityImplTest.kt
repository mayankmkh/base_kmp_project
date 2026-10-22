package dev.mayankmkh.basekmpproject.capability.identity.impl

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.SessionState
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStore
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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

    /** The screen that asked for sign-out may go away mid-way; the device still forgets. */
    @Test
    fun `a sign-out cancelled while the store clears still clears it`() = runTest {
        val removing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val real = inMemorySecretStores()
        val stores =
            object : SecretStores {
                override fun open(name: String): SecretStore {
                    val store = real.open(name)
                    return object : SecretStore by store {
                        override suspend fun remove(key: String) {
                            removing.complete(Unit)
                            release.await()
                            store.remove(key)
                        }
                    }
                }
            }
        val credentials = CredentialStore(stores, Logger)
        val capability = IdentityCapabilityImpl(credentials)
        capability.signIn(AuthToken("access"))

        val signingOut = launch { capability.signOut() }
        removing.await()
        signingOut.cancel()
        release.complete(Unit)
        signingOut.join()

        assertNull(credentials.getAuthToken())
    }

    private fun capability() =
        IdentityCapabilityImpl(CredentialStore(inMemorySecretStores(), Logger))
}
