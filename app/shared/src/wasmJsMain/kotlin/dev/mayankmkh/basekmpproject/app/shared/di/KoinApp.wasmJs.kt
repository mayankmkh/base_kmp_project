package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferencesContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.platform.securestorage.SecureStorageContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import org.koin.core.scope.Scope

// The browser's Network tab is the request log on web, and the console belongs to the host page.
internal actual fun Scope.isDebugBuild(): Boolean = false

internal actual fun Scope.createPreferencesContext(): PreferencesContext =
    PreferencesContext(ApplicationId)

internal actual fun Scope.createSecureStorageContext(): SecureStorageContext =
    SecureStorageContext()

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext()

internal actual fun Scope.createConnectivityContext(): ConnectivityContext = ConnectivityContext()
