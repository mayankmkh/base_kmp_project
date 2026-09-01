package dev.mayankmkh.basekmpproject.shared.features.list.presentation

import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import dev.mayankmkh.basekmpproject.shared.features.list.domain.GetItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ObserveReconnectsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.RefreshItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel.Event
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.ViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.stateInUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class ListViewModel(
    getItems: GetItemsUseCase,
    private val refreshItems: RefreshItemsUseCase,
    observeReconnects: ObserveReconnectsUseCase,
) : ViewModel<Event>() {
    val uiStateFlow: StateFlow<UiState<ItemsModel>> = getItems().stateInUiState(viewModelScope)

    private val refreshing = MutableStateFlow(false)

    /**
     * Whether a user-requested refresh is in flight.
     *
     * Separate from [uiStateFlow] on purpose: a refresh leaves the cached items on screen with an
     * indicator over them, where folding it into `UiState.InProgress` would blank the list.
     */
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    init {
        // Stale-while-revalidate, the second half: the cache is on screen the moment it can be, and
        // this is what re-checks it against the server once there is a network to ask. Without it a
        // screen opened offline would sit on stale rows until the user pulled it down themselves.
        //
        // Not `onStart`-ed onto `uiStateFlow`: that flow stops collecting five seconds after the
        // screen goes away, and a reconnection while the app is backgrounded is exactly the one
        // worth acting on.
        observeReconnects().onEach { refresh(notifyFailure = false) }.launchIn(viewModelScope)
    }

    internal fun onItemClicked(item: String) {
        send(Event.ItemClicked(item))
    }

    internal fun onRefresh() = refresh(notifyFailure = true)

    /**
     * One fetch path for both triggers, differing only in whether a failure is worth interrupting
     * for: a pull the user made deserves an answer, a reconnect they never asked about does not --
     * the use case has already reported it, and the list keeps showing what it had.
     *
     * Sharing the path is also what stops a pull and a reconnect landing together from fetching the
     * same feed twice.
     */
    private fun refresh(notifyFailure: Boolean) {
        // Claimed before launching, not inside the coroutine: `viewModelScope.launch` does not
        // necessarily run its body before this function returns, so a check made in there would
        // let a second pull straight through and fetch the same feed twice.
        if (!refreshing.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch {
            try {
                // The refreshed rows arrive through `uiStateFlow`, which is observing the cache.
                refreshItems().onErr { if (notifyFailure) send(Event.RefreshFailed(it)) }
            } finally {
                refreshing.value = false
            }
        }
    }

    internal sealed interface Event : ViewModel.Event {
        data class ItemClicked(val item: String) : Event

        data class RefreshFailed(val error: Throwable) : Event
    }
}
