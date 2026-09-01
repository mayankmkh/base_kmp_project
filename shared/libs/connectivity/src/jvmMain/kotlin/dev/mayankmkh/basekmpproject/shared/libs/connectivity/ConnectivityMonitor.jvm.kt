package dev.mayankmkh.basekmpproject.shared.libs.connectivity

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Always online.
 *
 * The JVM has no portable way to be told about network changes -- `NetworkInterface` can be polled,
 * but a desktop with an up interface and no route reads as online anyway, so the poll buys nothing.
 * The consequence is only that desktop never gets the reconnect-triggered refresh; a failed fetch
 * still surfaces as a failure, and pull-to-refresh still works.
 */
actual fun createConnectivityMonitor(context: ConnectivityContext): ConnectivityMonitor =
    ConnectivityMonitor {
        alwaysOnline
    }

// Emits once and then stays open rather than completing, so this actual has the same lifecycle as
// the three `callbackFlow` ones. A consumer that `combine`s on it should not see desktop behave
// differently for a reason that is not about desktop.
private val alwaysOnline: Flow<Boolean> = flow {
    emit(true)
    awaitCancellation()
}
