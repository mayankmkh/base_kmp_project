package __PACKAGE__.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import __PACKAGE__.__CELL__Content
import __PACKAGE__.__CELL__ViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// The Helix Cell signature is `(id, instanceKey, onOutput)`: `id` says *what* is shown,
// `instanceKey` says *which instance* owns the state, and `onOutput` is the only way out of the
// Feature. Two hosts may show this Cell at once as long as they pass different instance keys.
/** Independently hostable stateful presentation unit. */
@Composable
public fun __CELL__Cell(
    id: String,
    instanceKey: FeatureInstanceKey,
    onOutput: (__CELL__Output) -> Unit,
    modifier: Modifier = Modifier,
) {
    __CELL__Cell(id, instanceKey, onOutput, PaddingValues(), modifier)
}

@Composable
internal fun __CELL__Cell(
    id: String,
    instanceKey: FeatureInstanceKey,
    onOutput: (__CELL__Output) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viewModel: __CELL__ViewModel =
        koinViewModel(key = instanceKey.value, parameters = { parametersOf(id, instanceKey) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOutput by rememberUpdatedState(onOutput)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.outputs.collect { currentOnOutput(it) }
        }
    }

    Box(modifier.fillMaxSize().padding(contentPadding)) {
        __CELL__Content(state = state, onAction = viewModel::onAction)
    }
}
