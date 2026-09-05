package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import kotlinx.coroutines.flow.Flow

internal class CredentialStore(stores: SecretStores) {
    private val store = stores.open("identity.credentials")

    suspend fun getAuthToken(): String? = store.get(TokenKey)

    suspend fun setAuthToken(token: String) = store.set(TokenKey, token)

    suspend fun removeAuthToken() = store.remove(TokenKey)

    suspend fun hasAuthToken() = store.get(TokenKey) != null

    fun observeAuthToken(): Flow<String?> = store.observe(TokenKey)

    private companion object {
        const val TokenKey = "token"
    }
}
