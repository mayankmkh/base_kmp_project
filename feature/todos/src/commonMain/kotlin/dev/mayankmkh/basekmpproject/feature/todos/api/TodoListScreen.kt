package dev.mayankmkh.basekmpproject.feature.todos.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import base_kmp_project.feature.todos.generated.resources.Res
import base_kmp_project.feature.todos.generated.resources.input_invalid
import base_kmp_project.feature.todos.generated.resources.todo_unavailable
import dev.mayankmkh.basekmpproject.feature.todos.TodoListContent
import dev.mayankmkh.basekmpproject.feature.todos.TodoListViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodosUiCommand
import dev.mayankmkh.basekmpproject.foundation.presentation.CellPlacementId
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.ui.designsystem.messageResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
public fun TodoListScreen(
    instanceKey: FeatureInstanceKey,
    onOutput: (TodoListOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TodoListViewModel =
        koinViewModel(key = instanceKey.value, parameters = { parametersOf(instanceKey) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is TodosUiCommand.ShowFailure ->
                snackbarHostState.showSnackbar(getString(command.kind.messageResource()))
            TodosUiCommand.ShowTodoMissing ->
                snackbarHostState.showSnackbar(getString(Res.string.todo_unavailable))
            TodosUiCommand.ShowInputRejected ->
                snackbarHostState.showSnackbar(getString(Res.string.input_invalid))
        }
    }

    TodoListContent(
        state = state,
        onAction = viewModel::onAction,
        summary = {
            // The Screen is the host, so it derives the Cell's placement from its own identity.
            TodoSummaryCell(
                instanceKey =
                    FeatureInstanceKey.forPlacement(
                        surface = instanceKey.value,
                        cellType = "todo-summary",
                        placement = CellPlacementId.fromHostStableId("header"),
                    ),
                onOutput = {},
            )
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
