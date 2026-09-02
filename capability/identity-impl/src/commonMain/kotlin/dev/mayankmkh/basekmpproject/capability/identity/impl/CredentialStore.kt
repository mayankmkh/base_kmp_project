package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.foundation.preferences.PrefFile
import dev.mayankmkh.basekmpproject.foundation.preferences.PrefKey
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow

internal val CredentialsFile = PrefFile("credentials")

internal class CredentialStore(private val store: PreferenceStore) {

    suspend fun getAuthToken(): String? = store.getString(Keys.AUTH_TOKEN)

    suspend fun setAuthToken(token: String) = store.putString(Keys.AUTH_TOKEN, token)

    suspend fun removeAuthToken() = store.remove(Keys.AUTH_TOKEN)

    suspend fun hasAuthToken() = store.contains(Keys.AUTH_TOKEN)

    fun observeAuthToken(): Flow<String?> = store.observeString(Keys.AUTH_TOKEN)

    private enum class Keys(override val key: String) : PrefKey {
        AUTH_TOKEN("token")
    }
}
