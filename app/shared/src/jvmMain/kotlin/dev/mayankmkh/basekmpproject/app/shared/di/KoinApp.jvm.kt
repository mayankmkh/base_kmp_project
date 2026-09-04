package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferencesContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.platform.securestorage.SecureStorageContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import org.koin.core.scope.Scope

// jpackage stamps `jpackage.app-path` into every launcher it builds, so its absence means the app
// is running from Gradle or the IDE rather than an installed distribution.
internal actual fun Scope.isDebugBuild(): Boolean = System.getProperty("jpackage.app-path") == null

internal actual fun Scope.createPreferencesContext(): PreferencesContext =
    PreferencesContext(ApplicationId)

internal actual fun Scope.createSecureStorageContext(): SecureStorageContext =
    SecureStorageContext(ApplicationId)

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext()

internal actual fun Scope.createConnectivityContext(): ConnectivityContext = ConnectivityContext()
