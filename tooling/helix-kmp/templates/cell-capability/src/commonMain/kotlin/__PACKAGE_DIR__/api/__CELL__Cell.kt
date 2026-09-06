package __PACKAGE__.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import __CAP_PACKAGE__.__CAP_NAME__Id
import __PACKAGE__.__CELL__Content
import __PACKAGE__.__CELL__ViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.presentation.featureViewModel
import org.koin.core.parameter.parametersOf

/** Cell type of [__CELL__Cell]. */
public const val __CELL__CellType: String = "__CELL_TYPE__"

/** Independently hostable stateful presentation unit backed by the __CAP_NAME__ Capability. */
@Composable
public fun __CELL__Cell(
    id: __CAP_NAME__Id,
    instanceKey: FeatureInstanceKey,
    onOutput: (__CELL__Output) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: __CELL__ViewModel =
        featureViewModel(instanceKey, parameters = { parametersOf(id) })
    check(viewModel.id == id) {
        "FeatureInstanceKey '${instanceKey.value}' is already bound to id " +
            "'${viewModel.id.value}'; fold the id into the instance key"
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectWhileStarted(viewModel.outputs, onOutput)

    __CELL__Content(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier.padding(contentPadding),
    )
}
