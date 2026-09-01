package dev.mayankmkh.basekmpproject.shared.features.details.presentation

import androidx.lifecycle.viewModelScope
import dev.mayankmkh.basekmpproject.shared.features.details.domain.GetItemUseCase
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel.Event
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.ViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.stateInUiState
import kotlinx.coroutines.flow.StateFlow

internal class DetailsViewModel(itemId: String, getItem: GetItemUseCase) : ViewModel<Event>() {
    val uiStateFlow: StateFlow<UiState<Item>> =
        getItem(GetItemUseCase.Param(itemId)).stateInUiState(viewModelScope)

    internal fun onCloseClicked() {
        send(Event.Close)
    }

    internal sealed interface Event : ViewModel.Event {
        data object Close : Event
    }
}
