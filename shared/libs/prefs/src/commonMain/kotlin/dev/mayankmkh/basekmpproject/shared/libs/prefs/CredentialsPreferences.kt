package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

class CredentialsPreferences private constructor(private val dataStore: DataStore<Preferences>) {

    constructor(prefContext: PrefContext) : this(createDataStore(prefContext, PrefFile.CREDENTIALS))

    suspend fun getAuthToken(): String? = dataStore.getStringOrNull(Keys.AUTH_TOKEN)

    suspend fun setAuthToken(token: String) = dataStore.putString(Keys.AUTH_TOKEN, token)

    suspend fun removeAuthToken() = dataStore.remove(Keys.AUTH_TOKEN)

    suspend fun hasAuthToken() = dataStore.hasKey(Keys.AUTH_TOKEN)

    private enum class Keys(override val key: String) : PrefKey {
        AUTH_TOKEN("token")
    }

    fun getAuthTokenFlowable(): Flow<String?> = dataStore.getStringOrNullFlow(Keys.AUTH_TOKEN)
}
