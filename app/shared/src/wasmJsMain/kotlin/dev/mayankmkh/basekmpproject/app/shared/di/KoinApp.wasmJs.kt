package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.foundation.preferences.PrefContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import org.koin.core.scope.Scope

internal actual fun Scope.createPrefContext(): PrefContext = PrefContext()

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext()

internal actual fun Scope.createConnectivityContext(): ConnectivityContext = ConnectivityContext()
