package dev.mayankmkh.basekmpproject.app.shared.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.mayankmkh.basekmpproject.foundation.preferences.PrefContext
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.storage.database.DatabaseContext
import org.koin.core.scope.Scope

// The manifest flag the debug build type sets; works in a library module where `BuildConfig` is
// off.
internal actual fun Scope.isDebugBuild(): Boolean =
    get<Context>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

internal actual fun Scope.createPrefContext(): PrefContext = PrefContext(get())

internal actual fun Scope.createDatabaseContext(): DatabaseContext = DatabaseContext(get())

internal actual fun Scope.createConnectivityContext(): ConnectivityContext =
    ConnectivityContext(get())
