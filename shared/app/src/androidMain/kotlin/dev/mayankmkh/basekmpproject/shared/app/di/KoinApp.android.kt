package dev.mayankmkh.basekmpproject.shared.app.di

import dev.mayankmkh.basekmpproject.shared.libs.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.shared.libs.database.DatabaseContext
import dev.mayankmkh.basekmpproject.shared.libs.prefs.PrefContext
import org.koin.core.scope.Scope

internal actual fun Scope.createPrefContext(): PrefContext = PrefContext(get())

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext(get())

internal actual fun Scope.createConnectivityContext(): ConnectivityContext =
    ConnectivityContext(get())
