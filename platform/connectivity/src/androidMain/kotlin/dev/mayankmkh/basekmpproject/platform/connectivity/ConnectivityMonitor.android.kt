package dev.mayankmkh.basekmpproject.platform.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Follows the default network through `ConnectivityManager`.
 *
 * `registerDefaultNetworkCallback` reports only the network the app's traffic actually takes, so a
 * second network -- cellular behind Wi-Fi, say -- dropping cannot read as offline while the default
 * is still up. `NET_CAPABILITY_VALIDATED` is the interesting bit: a network can be connected and
 * still have no route out, and treating that as online would trigger a refresh that cannot work.
 */
actual fun createConnectivityMonitor(context: PlatformContext): ConnectivityMonitor =
    ConnectivityMonitor {
        callbackFlow {
            val manager =
                context.appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager

            if (manager == null) {
                // Nothing to watch. Reporting online is the safer default: it costs a fetch
                // that may fail, where reporting offline would suppress refreshes forever.
                send(true)
                awaitClose {}
                return@callbackFlow
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // From API 26 a capabilities callback always follows, and the platform
                        // documents reading capabilities here as racy; before that none may.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            trySend(manager.getNetworkCapabilities(network)?.hasInternet() == true)
                        }
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(networkCapabilities.hasInternet())
                    }

                    // The default network is gone; a replacement arrives as `onAvailable`.
                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }

            send(manager.hasValidatedNetwork())
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }
            .distinctUntilChanged()
    }

private fun ConnectivityManager.hasValidatedNetwork(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasInternet() == true

private fun NetworkCapabilities.hasInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
