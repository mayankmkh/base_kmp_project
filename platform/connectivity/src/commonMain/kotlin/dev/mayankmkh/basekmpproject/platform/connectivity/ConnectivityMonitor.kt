package dev.mayankmkh.basekmpproject.platform.connectivity

import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * Whether there is a network worth trying.
 *
 * Deliberately coarse. No platform can promise that a request will succeed -- a captive portal
 * answers every DNS lookup and nothing else -- so this is a hint, used to decide *when* to
 * synchronize rather than whether a fetch is allowed. A failed fetch is still handled as a failure.
 */
fun interface ConnectivityMonitor {
    /** Emits the current answer as soon as it is collected, then again on every change. */
    fun isOnline(): Flow<Boolean>
}

/**
 * One platform registration for every collector. The platform monitors are cold flows that register
 * a system callback per collector; sharing here lets every Capability pass `reconnects()` straight
 * to its coordinators. `Lazily` keeps the single registration for the app's lifetime, which is also
 * what makes a late collector's replayed value the current one rather than a stale one.
 */
fun ConnectivityMonitor.shared(scope: CoroutineScope): ConnectivityMonitor {
    val online = isOnline().shareIn(scope, SharingStarted.Lazily, replay = 1)
    return ConnectivityMonitor { online }
}

/**
 * Emits once every time the device comes back online.
 *
 * Here rather than in a caller because it corrects [isOnline]'s own contract, and the correction is
 * fiddly: the first emission is the state the device is *already* in, which is not a reconnection.
 * Acting on it would fire a fetch on every cold start, right behind the one the screen just made.
 * [drop]ping that first answer is what makes a `true` mean "this changed".
 *
 * The [distinctUntilChanged] is belt-and-braces -- every actual already collapses repeats -- but a
 * duplicate `true` reaching [drop] would be indistinguishable from a real reconnection.
 */
fun ConnectivityMonitor.reconnects(): Flow<Unit> =
    isOnline().distinctUntilChanged().drop(1).filter { it }.map {}

/** Creates the platform monitor with the app's shared [PlatformContext]. */
expect fun createConnectivityMonitor(context: PlatformContext): ConnectivityMonitor
