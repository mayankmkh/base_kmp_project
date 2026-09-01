package dev.mayankmkh.basekmpproject.shared.features.list.presentation

import androidx.lifecycle.viewModelScope
import dev.mayankmkh.basekmpproject.shared.features.list.domain.GetItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel.Event
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.ViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.mapLatestToUiStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal class ListViewModel(getItems: GetItemsUseCase) : ViewModel<Event>() {
    val uiStateFlow: StateFlow<UiState<ItemsModel>> =
        getItems()
            .mapLatestToUiStateFlow()
            .onStart { emit(UiState.InProgress) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Initial,
            )

    internal fun onItemClicked(item: String) {
        send(Event.ItemClicked(item))
    }

    internal sealed interface Event : ViewModel.Event {
        data class ItemClicked(val item: String) : Event
    }
}
