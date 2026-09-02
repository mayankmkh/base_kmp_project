package dev.mayankmkh.basekmpproject.capability.identity.api

import kotlinx.coroutines.flow.Flow

public interface IdentityQueries {
    public fun observeSession(): Flow<SessionState>
}

public interface IdentityCommands {
    /**
     * Establishes a session from a credential the app obtained elsewhere (this template has no
     * sign-in backend).
     */
    public suspend fun signIn(token: AuthToken)

    public suspend fun signOut()
}
