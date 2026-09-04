package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStore
import kotlinx.coroutines.flow.Flow

internal class CredentialStore(private val store: SecretStore) {

    suspend fun getAuthToken(): String? = store.get(TokenKey)

    suspend fun setAuthToken(token: String) = store.set(TokenKey, token)

    suspend fun removeAuthToken() = store.remove(TokenKey)

    suspend fun hasAuthToken() = store.get(TokenKey) != null

    fun observeAuthToken(): Flow<String?> = store.observe(TokenKey)

    private companion object {
        const val TokenKey = "token"
    }
}
