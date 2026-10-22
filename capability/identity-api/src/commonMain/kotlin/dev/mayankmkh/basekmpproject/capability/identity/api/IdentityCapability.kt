package dev.mayankmkh.basekmpproject.capability.identity.api

import kotlinx.coroutines.flow.Flow

public interface IdentityQueries {
    /**
     * The session as it changes. While the store holding the credential cannot be read (an OS vault
     * that is down, a Keychain before first unlock) the flow stays silent rather than reporting a
     * sign-out, and it resumes with the stored session once the store recovers.
     */
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
