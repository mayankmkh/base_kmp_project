package __PACKAGE__.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import __PACKAGE__.__CELL__Content

// A Cell fills the available width, sizes its height to its content, and never scrolls itself. The
// host supplies scrolling through `modifier` and insets through `contentPadding`, which the Cell
// applies inside its root so content can scroll under translucent system bars.
// `onOutput` stays the only way out of the Feature, with or without a ViewModel.
/**
 * Independently hostable presentation unit with no ViewModel of its own.
 *
 * Source of truth §12.15: a Cell owns a ViewModel only when it owns presentation state that must
 * outlive recomposition or a configuration change. This one does not, so it takes its data and its
 * callbacks as parameters, keeps its one piece of local UI state in `rememberSaveable`, and takes
 * no `FeatureInstanceKey` -- that key exists to scope a ViewModelStore there is none of here.
 */
@Composable
public fun __CELL__Cell(
    label: String,
    onOutput: (__CELL__Output) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    __CELL__Content(
        label = label,
        isExpanded = isExpanded,
        onExpandedChange = { expanded -> isExpanded = expanded },
        onSelect = { onOutput(__CELL__Output.Selected(label)) },
        onBack = { onOutput(__CELL__Output.Back) },
        modifier = modifier.padding(contentPadding),
    )
}
