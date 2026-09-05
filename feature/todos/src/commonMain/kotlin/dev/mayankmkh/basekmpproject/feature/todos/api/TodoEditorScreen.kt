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
import base_kmp_project.feature.todos.generated.resources.back
import base_kmp_project.feature.todos.generated.resources.new_todo
import dev.mayankmkh.basekmpproject.feature.todos.TodoEditorContent
import dev.mayankmkh.basekmpproject.feature.todos.TodoEditorViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodosUiCommand
import dev.mayankmkh.basekmpproject.feature.todos.messageResource
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TodoEditorScreen(
    instanceKey: FeatureInstanceKey,
    onOutput: (TodoEditorOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TodoEditorViewModel =
        koinViewModel(key = instanceKey.value, parameters = { parametersOf(instanceKey) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is TodosUiCommand.ShowFailure ->
                snackbarHostState.showSnackbar(getString(command.category.messageResource()))
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_todo)) },
                navigationIcon = {
                    TextButton(onClick = { onOutput(TodoEditorOutput.Back) }) {
                        Text(stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        TodoEditorContent(state, viewModel::onAction, Modifier.padding(padding))
    }
}
