package dev.mayankmkh.basekmpproject.capability.posts.impl

import dev.mayankmkh.basekmpproject.foundation.network.ApiError
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
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
internal class StoreResource<Key : Any, StoreValue : Any, Value : Any>(
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

    val observations: StateFlow<ResourceObservation<Value>> = state.asStateFlow()

    init {
        scope.launch {
            store.stream(StoreReadRequest.cached(key, refresh = false)).collect(::accept)
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
    suspend fun refresh(qos: RefreshQos) {
        // QoS is already part of the durable capability contract. This reference app has no
        // metered-network/scheduling mechanism yet, so every class executes immediately.
        scope.async { runRefresh() }.await()
    }

    private suspend fun runRefresh() {
        refreshMutex.withLock {
            state.update { it.copy(operation = ResourceOperation.Refreshing) }
            try {
                val terminal =
                    store.stream(StoreReadRequest.fresh(key)).first { response ->
                        response is StoreReadResponse.Data ||
                            response is StoreReadResponse.Error ||
                            response is StoreReadResponse.NoNewData
                    }
                accept(terminal)
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

    private fun accept(response: StoreReadResponse<StoreValue>) {
        when (response) {
            is StoreReadResponse.Data ->
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
            is StoreReadResponse.Loading ->
                state.update { it.copy(operation = ResourceOperation.Refreshing) }
            is StoreReadResponse.Error ->
                // A failure never clears the value it already holds, but it does demote it: the
                // last fetch did not confirm it.
                state.update { current ->
                    current.copy(
                        freshness =
                            if (current.value == null) ResourceFreshness.UNKNOWN
                            else ResourceFreshness.STALE,
                        operation = ResourceOperation.Failed(response.toResourceProblem()),
                    )
                }
            is StoreReadResponse.NoNewData ->
                // `Idle` without a value would violate `ResourceObservation`'s invariant. Store
                // only reports "nothing new" against something it already had, so this is a guard
                // rather than a live branch.
                state.update { current ->
                    if (current.value == null) current
                    else current.copy(operation = ResourceOperation.Idle)
                }
            StoreReadResponse.Initial -> Unit
        }
    }
}

private fun StoreReadResponse.Error.toResourceProblem(): ResourceProblem {
    val custom = (this as? StoreReadResponse.Error.Custom<*>)?.error as? ApiError
    return custom?.toResourceProblem()
        ?: when (this) {
            is StoreReadResponse.Error.Exception -> error.toResourceProblem()
            is StoreReadResponse.Error.Message -> unknownProblem()
            is StoreReadResponse.Error.Custom<*> -> unknownProblem()
        }
}

private fun ApiError.toResourceProblem(): ResourceProblem =
    when (this) {
        is ApiError.ServerResponse -> temporaryProblem()
        is ApiError.ClientRequest -> {
            val status = (throwable as? ResponseException)?.response?.status?.value
            when {
                status == HttpStatusCode.Unauthorized.value ||
                    status == HttpStatusCode.Forbidden.value -> accessProblem()
                status != null && status in CLIENT_ERROR_STATUSES -> permanentProblem()
                else -> unknownProblem()
            }
        }
        is ApiError.Unknown -> throwable.toResourceProblem()
        is ApiError.Redirect,
        is ApiError.OtherResponse -> unknownProblem()
    }

private val CLIENT_ERROR_STATUSES =
    HttpStatusCode.BadRequest.value until HttpStatusCode.InternalServerError.value

private fun Throwable.toResourceProblem(): ResourceProblem {
    val type = this::class.simpleName.orEmpty()
    val detail = message.orEmpty()
    return when {
        "Timeout" in type || "timed out" in detail.lowercase() -> temporaryProblem()
        OFFLINE_EXCEPTION_MARKERS.any { marker -> marker in type } -> offlineProblem()
        else -> unknownProblem()
    }
}

private val OFFLINE_EXCEPTION_MARKERS =
    listOf("IOException", "UnknownHost", "ConnectException", "NoRouteToHost")

private fun offlineProblem() =
    ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true, "No network connection")

private fun temporaryProblem() =
    ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true, "Temporarily unavailable")

private fun accessProblem() =
    ResourceProblem(ResourceProblemCategory.ACCESS, retryable = false, "Access denied")

private fun permanentProblem() =
    ResourceProblem(ResourceProblemCategory.PERMANENT, retryable = false, "Request rejected")

private fun unknownProblem() =
    ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = false, "Request failed")

private fun cancelledProblem() =
    ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = true, "Refresh was cancelled")
