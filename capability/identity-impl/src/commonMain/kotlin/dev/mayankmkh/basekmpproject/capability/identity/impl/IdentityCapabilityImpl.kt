package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.capability.identity.api.SessionState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The session as the product sees it. The network's `CredentialProvider` is
 * [CredentialStoreProvider], a separate object over the same [CredentialStore], so this class can
 * call the network without the client depending on it.
 */
internal class IdentityCapabilityImpl(private val credentials: CredentialStore) :
    IdentityQueries, IdentityCommands {

    override fun observeSession(): Flow<SessionState> =
        credentials
            .observeAuthToken()
            .map { token ->
                if (token == null) SessionState.Anonymous else SessionState.SignedIn
            }
            .distinctUntilChanged()

    override suspend fun signIn(token: AuthToken) {
        credentials.setAuthToken(token.value)
    }

    /** The screen that asked may go away mid-way; the device still forgets the credential. */
    override suspend fun signOut() {
        withContext(NonCancellable) { credentials.removeAuthToken() }
    }
}
