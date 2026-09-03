package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.capability.identity.api.SessionState
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import dev.mayankmkh.basekmpproject.foundation.network.CredentialRefreshResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class IdentityCapabilityImpl(private val credentials: CredentialStore) :
    IdentityQueries, IdentityCommands, CredentialProvider {

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

    override suspend fun signOut() {
        credentials.removeAuthToken()
    }

    override suspend fun currentBearerToken(): String? = credentials.getAuthToken()

    override suspend fun refreshBearerToken(): CredentialRefreshResult {
        // A real app first calls its refresh endpoint through an unauthenticated client created by
        // `createHttpClient(engine?, config)` with `AnonymousCredentialProvider` before deciding.
        signOut()
        return CredentialRefreshResult.Rejected
    }
}
