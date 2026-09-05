package dev.mayankmkh.basekmpproject.feature.todos.api

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import base_kmp_project.feature.todos.generated.resources.Res
import base_kmp_project.feature.todos.generated.resources.input_invalid
import base_kmp_project.feature.todos.generated.resources.todo_detail
import base_kmp_project.feature.todos.generated.resources.todo_unavailable
import base_kmp_project.ui.design_system.generated.resources.Res as DesignRes
import base_kmp_project.ui.design_system.generated.resources.back
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.feature.todos.TodoDetailContent
import dev.mayankmkh.basekmpproject.feature.todos.TodoDetailViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodosUiCommand
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.ui.designsystem.messageResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TodoDetailScreen(
    todoId: TodoId,
    instanceKey: FeatureInstanceKey,
    onOutput: (TodoDetailOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TodoDetailViewModel =
        koinViewModel(
            key = instanceKey.value,
            parameters = { parametersOf(todoId, instanceKey) },
        )
    check(viewModel.todoId == todoId) {
        "FeatureInstanceKey '${instanceKey.value}' is already bound to todo " +
            "${viewModel.todoId.value}; fold the todo id into the instance key"
    }
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.todo_detail, todoId.value)) },
                navigationIcon = {
                    TextButton(onClick = { onOutput(TodoDetailOutput.Back) }) {
                        Text(stringResource(DesignRes.string.back))
                    }
                },
            )
        },
    ) { padding ->
        TodoDetailContent(state, viewModel::onAction, Modifier.padding(padding))
    }
}
