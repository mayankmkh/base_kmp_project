package __PACKAGE__

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import __PACKAGE__.api.__CELL__Output
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Everything the __CELL__ Content needs to draw, and nothing else. */
internal data class __CELL__State(val id: String = "")

/** What the user did. Actions travel down; they are never navigation. */
internal sealed interface __CELL__Action {
    data object Select : __CELL__Action

    data object Back : __CELL__Action
}

// The instance key is what keeps two placements of the same Cell from sharing state, so it is
// required, not optional.
/** Owns this Cell instance's state for as long as the instance lives. */
internal class __CELL__ViewModel(
    private val id: String,
    instanceKey: FeatureInstanceKey,
) : ViewModel() {
    private val mutableState = MutableStateFlow(__CELL__State(id = id))
    private val outputChannel = Channel<__CELL__Output>(Channel.BUFFERED)

    val state: StateFlow<__CELL__State> = mutableState.asStateFlow()
    val outputs: Flow<__CELL__Output> = outputChannel.receiveAsFlow()

    init {
        require(instanceKey.value.isNotBlank())
    }

    // Each branch delegates to a short private function so that the formatting of this file does
    // not depend on how long the Cell's name happens to be.
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
