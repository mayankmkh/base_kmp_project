package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import dev.mayankmkh.basekmpproject.foundation.network.CredentialRefreshResult
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStoreException

/**
 * Supplies the stored credential to the HTTP client, and nothing else.
 *
 * Deliberately a separate object from [IdentityCapabilityImpl]: the client is built with a
 * `CredentialProvider`, so whatever is bound as one is constructed before the client exists. Once
 * Identity calls the network itself (a sign-in or a refresh endpoint), binding the capability as
 * the provider too would close the loop `HttpClient -> CredentialProvider -> IdentityCapabilityImpl
 * -> HttpClient`. Depending only on the [CredentialStore] keeps this end of the graph a leaf. A
 * real refresh that needs the client reaches it lazily (Koin `inject()` or a `Lazy<HttpClient>`),
 * or through a separate client that never sends a credential.
 */
internal class CredentialStoreProvider(private val credentials: CredentialStore) :
    CredentialProvider {

    /**
     * A store that cannot be read right now sends the request without a credential rather than
     * failing it; nothing is cleared, so the stored credential is back once the store reads again.
     * A 401 meanwhile either finds the credential again, once the store has recovered, or reaches
     * [refreshCredential] without one, which clears nothing.
     */
    override suspend fun currentCredential(): String? =
        try {
            credentials.getAuthToken()
        } catch (failure: SecretStoreException) {
            credentials.warnUnreadable(failure)
            null
        }

    /**
     * There is no refresh endpoint, so a rejected credential is a finished session. Only the
     * credential that was rejected is cleared: a 401 that arrives after the user has signed in
     * again belongs to the old credential and must not end the new session, and a request that
     * carried no credential says nothing about the one stored now.
     */
    override suspend fun refreshCredential(rejected: String?): CredentialRefreshResult {
        // A real refresh would call the token endpoint without marking the request
        // `authenticated()`, so it never enters the credential refresh loop.
        if (rejected != null) credentials.removeAuthToken(ifCurrent = rejected)
        return CredentialRefreshResult.Rejected
    }
}
