package dev.mayankmkh.basekmpproject.platform.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Whether there is a network worth trying.
 *
 * Deliberately coarse. No platform can promise that a request will succeed -- a captive portal
 * answers every DNS lookup and nothing else -- so this is a hint, used to decide *when* to
 * revalidate rather than whether a fetch is allowed. A failed fetch is still handled as a failure.
 */
fun interface ConnectivityMonitor {
    /** Emits the current answer as soon as it is collected, then again on every change. */
    fun isOnline(): Flow<Boolean>
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

expect fun createConnectivityMonitor(context: ConnectivityContext): ConnectivityMonitor
