package dev.mayankmkh.basekmpproject.foundation.resource.store5

import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.toNetworkFailure
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.StoreReadResponseOrigin

/**
 * Adapts one Store key to the offline-first [ResourceObservation] contract: a value from the source
 * of truth is served immediately as STALE, a fetcher-origin value promotes it to FRESH, and a
 * failure never clears the value it already holds.
 */
public class StoreResource<Key : Any, StoreValue : Any, Value : Any>(
    private val scope: CoroutineScope,
    private val store: Store<Key, StoreValue>,
    private val key: Key,
    private val mapValue: (StoreValue) -> Value,
) {
    // One flow, not a value flow combined with a sync flow: `combine` collects its sources in
    // separate coroutines, so on a multi-threaded dispatcher it can observe a new operation before
    // the value that came with it and build an `Idle` observation without a value, which the
    // `ResourceObservation` invariant rejects. Every transition below is a single atomic update
    // that constructs a valid observation or none.
    private val state = MutableStateFlow(ResourceObservation.initial<Value>())
    private val refreshMutex = Mutex()

    public val observations: StateFlow<ResourceObservation<Value>> = state.asStateFlow()

    init {
        scope.launch {
            store.stream(StoreReadRequest.cached(key, refresh = false)).collect { accept(it) }
        }
    }

    /**
     * Runs on the capability's own [scope] rather than the caller's.
     *
     * The observation is shared by every observer of this key, so a fetch that publishes
     * `Refreshing` has to publish its terminal state too. Callers are ViewModels launching into
     * `viewModelScope`: leaving the fetch in the caller would let a cleared ViewModel cancel it
     * between those two writes and strand every remaining observer on `Refreshing` forever. `async
     * { }.await()` keeps the caller's cancellation meaningful -- it stops *waiting* -- while the
     * fetch and the state it owns run to completion under the capability's lifetime.
     */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun refresh(qos: RefreshQos): RefreshOutcome {
        // QoS is already part of the durable capability contract. This reference app has no
        // metered-network/scheduling mechanism yet, so every class executes immediately.
        return scope.async { runRefresh() }.await()
    }

    private suspend fun runRefresh(): RefreshOutcome {
        return refreshMutex.withLock {
            state.update { it.copy(operation = ResourceOperation.Refreshing) }
            try {
                val terminal =
                    store.stream(StoreReadRequest.fresh(key)).first { response ->
                        response is StoreReadResponse.Data ||
                            response is StoreReadResponse.Error ||
                            response is StoreReadResponse.NoNewData
                    }
                val problem = accept(terminal)
                if (problem == null) RefreshOutcome.Succeeded else RefreshOutcome.Failed(problem)
            } finally {
                // Only reached with `Refreshing` still set when the fetch never produced a terminal
                // response -- the capability scope was cancelled mid-flight. Settling here keeps
                // the promise the `Refreshing` write made.
                settleAbandonedRefresh()
            }
        }
    }

    private fun settleAbandonedRefresh() {
        state.update { current ->
            if (current.operation !is ResourceOperation.Refreshing) {
                current
            } else {
                // `ResourceObservation` forbids `Idle` without a value, so an abandoned first load
                // reports the cancellation as a retryable failure rather than a finished one.
                current.copy(
                    operation =
                        if (current.value == null) {
                            ResourceOperation.Failed(cancelledProblem())
                        } else {
                            ResourceOperation.Idle
                        }
                )
            }
        }
    }

    private suspend fun accept(response: StoreReadResponse<StoreValue>): ResourceProblem? {
        return when (response) {
            is StoreReadResponse.Data -> {
                state.value =
                    ResourceObservation(
                        value = mapValue(response.value),
                        freshness =
                            if (response.origin is StoreReadResponseOrigin.Fetcher) {
                                ResourceFreshness.FRESH
                            } else {
                                ResourceFreshness.STALE
                            },
                        operation = ResourceOperation.Idle,
                    )
                null
            }
            is StoreReadResponse.Loading -> {
                state.update { it.copy(operation = ResourceOperation.Refreshing) }
                null
            }
            is StoreReadResponse.Error -> {
                // A failure never clears the value it already holds, but it does demote it: the
                // last fetch did not confirm it.
                val problem = response.toResourceProblem()
                state.update { current ->
                    current.copy(
                        freshness =
                            if (current.value == null) ResourceFreshness.UNKNOWN
                            else ResourceFreshness.STALE,
                        operation = ResourceOperation.Failed(problem),
                    )
                }
                problem
            }
            is StoreReadResponse.NoNewData -> {
                // `Idle` without a value would violate `ResourceObservation`'s invariant. Store
                // only reports "nothing new" against something it already had, so this is a guard
                // rather than a live branch.
                state.update { current ->
                    if (current.value == null) current
                    else current.copy(operation = ResourceOperation.Idle)
                }
                null
            }
            StoreReadResponse.Initial -> null
        }
    }
}

private suspend fun StoreReadResponse.Error.toResourceProblem(): ResourceProblem {
    val custom = (this as? StoreReadResponse.Error.Custom<*>)?.error as? NetworkFailure
    return custom?.toResourceProblem()
        ?: when (this) {
            is StoreReadResponse.Error.Exception -> error.toNetworkFailure().toResourceProblem()
            is StoreReadResponse.Error.Message,
            is StoreReadResponse.Error.Custom<*> ->
                ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = false)
        }
}

private fun cancelledProblem() = ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = true)
