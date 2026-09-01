package dev.mayankmkh.basekmpproject.shared.libs.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Follows the default network through `ConnectivityManager`.
 *
 * `registerNetworkCallback` uses an explicit internet-capable request so capability changes drive
 * the monitor. `NET_CAPABILITY_VALIDATED` is the interesting bit: a network can be connected and
 * still have no route out, and treating that as online would trigger a refresh that cannot work.
 */
actual fun createConnectivityMonitor(context: ConnectivityContext): ConnectivityMonitor =
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
                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(networkCapabilities.hasInternet())
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }

                send(manager.hasValidatedNetwork())
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }
            .distinctUntilChanged()
    }

private fun ConnectivityManager.hasValidatedNetwork(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasInternet() == true

private fun NetworkCapabilities.hasInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
