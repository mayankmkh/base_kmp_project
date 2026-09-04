package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferencesContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.platform.securestorage.SecureStorageContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.scope.Scope

// True for the framework's debug binary, which is what an Xcode Debug configuration links.
@OptIn(ExperimentalNativeApi::class)
internal actual fun Scope.isDebugBuild(): Boolean = Platform.isDebugBinary

internal actual fun Scope.createPreferencesContext(): PreferencesContext = PreferencesContext()

internal actual fun Scope.createSecureStorageContext(): SecureStorageContext =
    SecureStorageContext(ApplicationId)

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext()

internal actual fun Scope.createConnectivityContext(): ConnectivityContext = ConnectivityContext()
