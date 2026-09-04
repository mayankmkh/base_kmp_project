package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.foundation.preferences.PrefContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import org.koin.core.scope.Scope

// The browser's Network tab is the request log on web, and the console belongs to the host page.
internal actual fun Scope.isDebugBuild(): Boolean = false

internal actual fun Scope.createPrefContext(): PrefContext = PrefContext()

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext()

internal actual fun Scope.createConnectivityContext(): ConnectivityContext = ConnectivityContext()
