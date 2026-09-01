package dev.mayankmkh.basekmpproject.shared.libs.connectivity

/**
 * Whatever the platform needs in order to be asked about the network.
 *
 * Android is the only target that needs anything -- a `Context`, to reach `ConnectivityManager` --
 * so the other three actuals are empty. Mirrors `PrefContext` and `DatabaseContext`: the app
 * already knows how to supply one platform-shaped handle per library.
 */
expect class ConnectivityContext
