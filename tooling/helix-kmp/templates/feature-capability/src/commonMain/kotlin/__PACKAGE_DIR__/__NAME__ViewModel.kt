package __PACKAGE__

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import __CAP_PACKAGE__.__CAP_NAME__Commands
import __CAP_PACKAGE__.__CAP_NAME__Id
import __CAP_PACKAGE__.__CAP_NAME__Queries
import __CAP_PACKAGE__.__CAP_NAME__Record
import __PACKAGE__.api.__NAME__Output
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.failure
import dev.mayankmkh.basekmpproject.foundation.resource.hasValue
import dev.mayankmkh.basekmpproject.foundation.resource.isRefreshing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Content needs to draw, and nothing else. */
internal data class __NAME__State(
    val id: String = "",
    val label: String? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val problem: ResourceProblem? = null,
)

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

// Reads through the Capability's grouped `Queries` and asks for synchronization through its intent
// `Commands`. The Capability owns the data; this ViewModel owns only the presentation of it.
//
// Collecting the Query is what starts the Capability's appearance sync, so nothing is requested
// here on start. `WhileSubscribed` re-collects for a returning subscriber and the Capability's
// interval decides whether that deserves another sync, which absorbs quick back-and-forth
// navigation. The persistent problem lives in State; transient feedback comes only from the
// command outcome. `:feature:posts` (`PostDetailViewModel`) is the reference.
/** Owns this Cell instance's state for as long as the instance lives. */
internal class __NAME__ViewModel(
    val id: String,
    instanceKey: FeatureInstanceKey,
    queries: __CAP_NAME__Queries,
    private val commands: __CAP_NAME__Commands,
) : ViewModel() {
    private val uiCommandChannel = Channel<__NAME__UiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<__NAME__Output>(Channel.BUFFERED)

    val uiCommands: Flow<__NAME__UiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<__NAME__Output> = outputChannel.receiveAsFlow()
    val state: StateFlow<__NAME__State> =
        queries
            .observe(__CAP_NAME__Id(id))
            .map { observation -> observation.toState(id) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                __NAME__State(id = id),
            )

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

    private fun select() {
        viewModelScope.launch { outputChannel.send(__NAME__Output.Selected(id)) }
    }

    private fun back() {
        viewModelScope.launch { outputChannel.send(__NAME__Output.Back) }
    }

    private fun refresh() {
        viewModelScope.launch {
            val outcome = commands.refresh()
            if (outcome is RefreshOutcome.Failed) {
                // Product code should map this stable category to a localized string resource.
                val message = outcome.problem.category.name
                uiCommandChannel.send(__NAME__UiCommand.ShowMessage(message))
            }
        }
    }
}

private fun ResourceObservation<__CAP_NAME__Record>.toState(id: String) =
    __NAME__State(
        id = id,
        label = value?.label,
        isInitialLoading = !hasValue && isRefreshing,
        isRefreshing = hasValue && isRefreshing,
        problem = failure,
    )
