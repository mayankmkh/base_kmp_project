package dev.mayankmkh.basekmpproject.platform.connectivity

/**
 * Whatever the platform needs in order to be asked about the network.
 *
 * Android is the only target that needs anything -- a `Context`, to reach `ConnectivityManager` --
 * so the other three actuals are empty. Mirrors `PrefContext` in `:foundation:preferences` and
 * `DatabaseContext`: the app already knows how to supply one platform-shaped handle per library.
 */
expect class ConnectivityContext
