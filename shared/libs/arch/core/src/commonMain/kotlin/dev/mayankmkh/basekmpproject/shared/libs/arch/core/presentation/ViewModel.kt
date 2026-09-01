package dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// The project's presentation base type, delegating retention and clearing to AndroidX.
//
// Abstract with nothing abstract in it on purpose: this is the type feature view models extend, and
// there is no such thing as a bare `ViewModel<E>` to instantiate. detekt reads that as a concrete
// class waiting to happen.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class ViewModel<E : ViewModel.Event> : androidx.lifecycle.ViewModel() {
    private val eventChannel = Channel<E>(Channel.BUFFERED)
    val eventsFlow: Flow<E> = eventChannel.receiveAsFlow()

    protected fun send(event: E) {
        viewModelScope.launch { eventChannel.send(event) }
    }

    interface Event
}
