package __PACKAGE__

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import __CAP_PACKAGE__.__CAP_NAME__Id
import __CAP_PACKAGE__.__CAP_NAME__Queries
import __PACKAGE__.api.__CELL__Output
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the __CELL__ Content needs to draw, and nothing else. */
internal data class __CELL__State(val id: String = "")

/** What the user did. Actions travel down; they are never navigation. */
internal sealed interface __CELL__Action {
    data object Select : __CELL__Action

    data object Back : __CELL__Action
}

/** Owns this Cell instance's state for as long as the instance lives. */
internal class __CELL__ViewModel(
    val id: __CAP_NAME__Id,
    queries: __CAP_NAME__Queries,
) : ViewModel() {
    private val outputChannel = Channel<__CELL__Output>(Channel.BUFFERED)

    val state: StateFlow<__CELL__State> =
        queries
            .observe(id)
            .map { observation -> __CELL__State(observation.value?.label ?: id.value) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                __CELL__State(id.value),
            )
    val outputs: Flow<__CELL__Output> = outputChannel.receiveAsFlow()

    fun onAction(action: __CELL__Action) {
        when (action) {
            __CELL__Action.Select -> select()
            __CELL__Action.Back -> back()
        }
    }

    private fun select() {
        viewModelScope.launch { outputChannel.send(__CELL__Output.Selected(id)) }
    }

    private fun back() {
        viewModelScope.launch { outputChannel.send(__CELL__Output.Back) }
    }
}
