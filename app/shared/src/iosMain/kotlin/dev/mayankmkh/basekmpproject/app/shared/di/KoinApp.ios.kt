package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.foundation.preferences.PrefContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.scope.Scope

// True for the framework's debug binary, which is what an Xcode Debug configuration links.
@OptIn(ExperimentalNativeApi::class)
internal actual fun Scope.isDebugBuild(): Boolean = Platform.isDebugBinary

internal actual fun Scope.createPrefContext(): PrefContext = PrefContext()

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext()

internal actual fun Scope.createConnectivityContext(): ConnectivityContext = ConnectivityContext()
