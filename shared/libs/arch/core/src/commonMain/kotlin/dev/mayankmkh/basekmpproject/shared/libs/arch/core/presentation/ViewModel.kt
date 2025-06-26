package dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.ViewModel.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BasicViewModel : InstanceKeeper.Instance {
    protected val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        coroutineScope.cancel()
    }
}

abstract class ViewModel<E : Event> : BasicViewModel() {
    private val eventChannel = Channel<E>(Channel.BUFFERED)
    val eventsFlow: Flow<E> = eventChannel.receiveAsFlow()

    protected fun send(event: E) {
        coroutineScope.launch { eventChannel.send(event) }
    }

    interface Event
}
