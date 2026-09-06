package __PACKAGE__.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import __PACKAGE__.__CELL__Content
import __PACKAGE__.__CELL__ViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.presentation.featureViewModel
import org.koin.core.parameter.parametersOf

/** Cell type of [__CELL__Cell]. */
public const val __CELL__CellType: String = "__CELL_TYPE__"

// The Helix Cell signature is `(id, instanceKey, onOutput)`: `id` says *what* is shown,
// `instanceKey` says *which instance* owns the state, and `onOutput` is the only way out of the
// Feature. Two hosts may show this Cell at once as long as they pass different instance keys.
// A Cell fills the available width, sizes its height to its content, and never scrolls itself. The
// host supplies scrolling through `modifier` and insets through `contentPadding`, which the Cell
// applies inside its root so content can scroll under translucent system bars.
// The identity guard fails fast when a host reuses one placement key for a different `id`: the
// ViewModel is keyed by `instanceKey` alone, so the stale instance would otherwise keep rendering.
/** Independently hostable stateful presentation unit. */
@Composable
public fun __CELL__Cell(
    id: String,
    instanceKey: FeatureInstanceKey,
    onOutput: (__CELL__Output) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: __CELL__ViewModel =
        featureViewModel(instanceKey, parameters = { parametersOf(id) })
    check(viewModel.id == id) {
        "FeatureInstanceKey '${instanceKey.value}' is already bound to id " +
            "'${viewModel.id}'; fold the id into the instance key"
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectWhileStarted(viewModel.outputs, onOutput)

    __CELL__Content(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier.padding(contentPadding),
    )
}
