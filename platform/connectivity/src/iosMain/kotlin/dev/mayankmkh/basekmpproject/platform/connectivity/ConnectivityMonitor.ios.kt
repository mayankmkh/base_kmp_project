package dev.mayankmkh.basekmpproject.platform.connectivity

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue

/**
 * Follows the system's path status through `NWPathMonitor`.
 *
 * `nw_path_status_satisfied` is Apple's own "a connection could be made" answer, which is the same
 * question the other platforms are being asked. The handler runs on a global queue rather than the
 * main one: nothing here touches UI, and the flow's collector decides its own context.
 */
actual fun createConnectivityMonitor(context: ConnectivityContext): ConnectivityMonitor =
    ConnectivityMonitor {
        callbackFlow {
                val monitor = nw_path_monitor_create()
                nw_path_monitor_set_update_handler(monitor) { path ->
                    trySend(nw_path_get_status(path) == nw_path_status_satisfied)
                }
                nw_path_monitor_set_queue(
                    monitor,
                    dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u),
                )
                nw_path_monitor_start(monitor)
                awaitClose { nw_path_monitor_cancel(monitor) }
            }
            .distinctUntilChanged()
    }
