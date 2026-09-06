package dev.mayankmkh.basekmpproject.feature.todos.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mayankmkh.basekmpproject.feature.todos.TodoSummaryContent
import dev.mayankmkh.basekmpproject.feature.todos.TodoSummaryViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.presentation.featureViewModel

/**
 * Independently hostable counts with presentation identity separate from Todo resource identity.
 */
@Composable
public fun TodoSummaryCell(
    instanceKey: FeatureInstanceKey,
    onOutput: (TodoSummaryOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TodoSummaryViewModel = featureViewModel(instanceKey)
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectWhileStarted(viewModel.outputs, onOutput)
    TodoSummaryContent(state, viewModel::onAction, modifier)
}
