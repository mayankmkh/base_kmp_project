package dev.mayankmkh.basekmpproject.shared.libs.prefs

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
class CredentialsPreferences private constructor(private val settings: FlowSettings) {

    constructor(
        prefContext: PrefContext
    ) : this(createDataStoreSettings(prefContext, PrefFile.CREDENTIALS))

    suspend fun getAuthToken(): String? = settings.getStringOrNull(Keys.AUTH_TOKEN.key)

    suspend fun setAuthToken(token: String) = settings.putString(Keys.AUTH_TOKEN.key, token)

    suspend fun removeAuthToken() = settings.remove(Keys.AUTH_TOKEN.key)

    suspend fun hasAuthToken() = settings.hasKey(Keys.AUTH_TOKEN.key)

    private enum class Keys(override val key: String) : PrefKey {
        AUTH_TOKEN("token")
    }

    fun getAuthTokenFlowable(): Flow<String?> = settings.getStringOrNullFlow(Keys.AUTH_TOKEN.key)
}
