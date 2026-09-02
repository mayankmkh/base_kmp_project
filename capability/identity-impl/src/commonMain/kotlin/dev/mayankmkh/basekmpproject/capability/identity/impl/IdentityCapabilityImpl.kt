package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.capability.identity.api.SessionState
import dev.mayankmkh.basekmpproject.foundation.network.BearerTokenSource
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class IdentityCapabilityImpl(private val credentials: CredentialStore) :
    IdentityQueries, IdentityCommands, BearerTokenSource {

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

    override suspend fun getAuthToken(): String? = credentials.getAuthToken()

    override suspend fun getRefreshToken(): String? = null

    override suspend fun HttpClient.refreshToken() {
        // A real app calls its refresh endpoint here.
    }

    override suspend fun refreshUnauthorized() {
        signOut()
    }
}
