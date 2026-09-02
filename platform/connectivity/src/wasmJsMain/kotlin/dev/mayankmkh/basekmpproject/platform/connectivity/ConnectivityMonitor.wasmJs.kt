package dev.mayankmkh.basekmpproject.platform.connectivity

import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.w3c.dom.events.Event

/**
 * The browser's own answer, from `navigator.onLine` and the events that change it.
 *
 * Worth knowing: `onLine` is false only when the browser knows it has no connection at all. A
 * machine on a network that cannot reach the internet reports true, so this is the weakest of the
 * four implementations -- which is why a failed fetch, not this flow, is what reports a problem.
 */
actual fun createConnectivityMonitor(context: ConnectivityContext): ConnectivityMonitor =
    ConnectivityMonitor {
        callbackFlow {
                val onOnline: (Event) -> Unit = { trySend(true) }
                val onOffline: (Event) -> Unit = { trySend(false) }

                send(window.navigator.onLine)
                window.addEventListener(type = "online", callback = onOnline)
                window.addEventListener(type = "offline", callback = onOffline)
                awaitClose {
                    window.removeEventListener(type = "online", callback = onOnline)
                    window.removeEventListener(type = "offline", callback = onOffline)
                }
            }
            .distinctUntilChanged()
    }
