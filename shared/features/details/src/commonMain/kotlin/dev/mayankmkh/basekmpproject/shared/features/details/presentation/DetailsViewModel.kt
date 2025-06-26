package dev.mayankmkh.basekmpproject.shared.features.details.presentation

import dev.mayankmkh.basekmpproject.shared.features.details.domain.GetItemUseCase
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel.Event
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.ViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.toUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal class DetailsViewModel(private val itemId: String, getItem: GetItemUseCase) :
    ViewModel<Event>() {
    val uiStateFlow: StateFlow<UiState<Item>> =
        flow {
                val result = getItem(GetItemUseCase.Param(itemId))
                emit(result.toUiState())
            }
            .onStart { emit(UiState.InProgress) }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Initial,
            )

    internal fun onCloseClicked() {
        send(Event.Close)
    }

    internal sealed class Event : ViewModel.Event {
        internal data object Close : Event()
    }
}
