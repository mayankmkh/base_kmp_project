package __PACKAGE__.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import __PACKAGE__.__NAME__Content
import __PACKAGE__.__NAME__UiCommand
import __PACKAGE__.__NAME__ViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// The Helix Cell signature is `(id, instanceKey, onOutput)`: `id` says *what* is shown,
// `instanceKey` says *which instance* owns the state, and `onOutput` is the only way out of the
// Feature.
/** Independently hostable stateful presentation unit. */
@Composable
public fun __NAME__Cell(
    id: String,
    instanceKey: FeatureInstanceKey,
    onOutput: (__NAME__Output) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: __NAME__ViewModel =
        koinViewModel(key = instanceKey.value, parameters = { parametersOf(id, instanceKey) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is __NAME__UiCommand.ShowMessage -> snackbarHostState.showSnackbar(command.message)
        }
    }

    Box(modifier.fillMaxSize().padding(contentPadding)) {
        __NAME__Content(state = state, onAction = viewModel::onAction)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
