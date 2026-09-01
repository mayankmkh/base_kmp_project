package dev.mayankmkh.basekmpproject.shared.features.list.domain

import com.github.michaelbull.result.Ok
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UnitFlowUseCase
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.reconnects
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlinx.coroutines.flow.map

/**
 * Emits once every time the device comes back online.
 *
 * Thin on purpose: what counts as a reconnection is [reconnects]' business, because it is a
 * property of the monitor's contract rather than of this screen. All this adds is the layering --
 * dispatcher and failure reporting -- so another feature wanting the same trigger writes these
 * seven lines instead of re-deriving the rule.
 */
internal class ObserveReconnectsUseCase(
    private val connectivityMonitor: ConnectivityMonitor,
    appDispatchers: AppDispatchers,
    failureListener: UseCaseFailureListener,
    // `cpu`: nothing here touches disk or network. The platform monitors deliver on threads of
    // their own choosing, so this is only about where the filtering runs.
) : UnitFlowUseCase<Unit>(appDispatchers.cpu, failureListener) {
    override fun execute() = connectivityMonitor.reconnects().map { Ok(Unit) }
}
