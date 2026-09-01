package dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// Keeps the project's presentation base type while delegating retention and clearing to AndroidX.
abstract class BasicViewModel : androidx.lifecycle.ViewModel()

abstract class ViewModel<E : ViewModel.Event> : BasicViewModel() {
    private val eventChannel = Channel<E>(Channel.BUFFERED)
    val eventsFlow: Flow<E> = eventChannel.receiveAsFlow()

    protected fun send(event: E) {
        viewModelScope.launch { eventChannel.send(event) }
    }

    interface Event
}
