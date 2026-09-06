package __PACKAGE__.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import dev.mayankmkh.basekmpproject.foundation.presentation.featureViewModel
import org.koin.core.parameter.parametersOf

// The Helix Cell signature is `(id, instanceKey, onOutput)`: `id` says *what* is shown,
// `instanceKey` says *which instance* owns the state, and `onOutput` is the only way out of the
// Feature. A Cell fills the available width, sizes its height to its content, and never scrolls
// itself. The host supplies scrolling through `modifier` and insets through `contentPadding`,
// which the Cell applies inside its root so content can scroll under translucent system bars.
// Surface-level element state such as `SnackbarHostState` is hoisted as a nullable parameter:
// `null` means the Cell self-hosts a snackbar; a non-null state belongs to the host surface, which
// then renders the snackbar host itself.
// The identity guard fails fast when a host reuses one placement key for a different `id`: the
// ViewModel is keyed by `instanceKey` alone, so the stale instance would otherwise keep rendering.
/** Independently hostable stateful presentation unit. */
@Composable
public fun __NAME__Cell(
    id: String,
    instanceKey: FeatureInstanceKey,
    onOutput: (__NAME__Output) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState? = null,
) {
    val viewModel: __NAME__ViewModel =
        featureViewModel(instanceKey, parameters = { parametersOf(id) })
    check(viewModel.id == id) {
        "FeatureInstanceKey '${instanceKey.value}' is already bound to id " +
            "'${viewModel.id}'; fold the id into the instance key"
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = snackbarHostState ?: remember { SnackbarHostState() }

    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is __NAME__UiCommand.ShowMessage -> {
                snackbarHost.showSnackbar(command.message)
            }
        }
    }

    Box(modifier) {
        __NAME__Content(
            state = state,
            onAction = viewModel::onAction,
            modifier = Modifier.padding(contentPadding),
        )
        if (snackbarHostState == null) {
            SnackbarHost(
                snackbarHost,
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}
