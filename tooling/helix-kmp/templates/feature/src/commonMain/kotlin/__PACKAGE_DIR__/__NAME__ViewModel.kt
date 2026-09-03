package __PACKAGE__

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import __PACKAGE__.api.__NAME__Output
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Everything the Content needs to draw, and nothing else. */
internal data class __NAME__State(val id: String = "", val isBusy: Boolean = false)

/** What the user did. Actions travel down; they are never navigation. */
internal sealed interface __NAME__Action {
    data object Refresh : __NAME__Action

    data object Select : __NAME__Action

    data object Back : __NAME__Action
}

/** One-shot presentation effects. A UiCommand never carries correctness-bearing state. */
internal sealed interface __NAME__UiCommand {
    data class ShowMessage(val message: String) : __NAME__UiCommand
}

// This scaffold is self-contained on purpose: it reads no product data, so it depends on no
// Capability.
//
// Next step: when it needs data, inject a Capability's grouped `Queries` and intent `Commands`
// and map `ResourceObservation` into the State -- `:feature:posts` (`PostDetailViewModel`) is the
// reference. A Feature depends on `:capability:*-api` only, never on an `-impl` module.
/** Owns this Cell instance's state for as long as the instance lives. */
internal class __NAME__ViewModel(
    val id: String,
    instanceKey: FeatureInstanceKey,
) : ViewModel() {
    private val mutableState = MutableStateFlow(__NAME__State(id = id))
    private val uiCommandChannel = Channel<__NAME__UiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<__NAME__Output>(Channel.BUFFERED)

    val state: StateFlow<__NAME__State> = mutableState.asStateFlow()
    val uiCommands: Flow<__NAME__UiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<__NAME__Output> = outputChannel.receiveAsFlow()

    init {
        require(instanceKey.value.isNotBlank())
    }

    // Each branch delegates to a short private function so that the formatting of this file does
    // not depend on how long the Feature's name happens to be.
    fun onAction(action: __NAME__Action) {
        when (action) {
            __NAME__Action.Refresh -> refresh()
            __NAME__Action.Select -> select()
            __NAME__Action.Back -> back()
        }
    }

    private fun refresh() {
        val message = __NAME__UiCommand.ShowMessage("Nothing to refresh yet")
        viewModelScope.launch { uiCommandChannel.send(message) }
    }

    private fun select() {
        viewModelScope.launch { outputChannel.send(__NAME__Output.Selected(id)) }
    }

    private fun back() {
        viewModelScope.launch { outputChannel.send(__NAME__Output.Back) }
    }
}
