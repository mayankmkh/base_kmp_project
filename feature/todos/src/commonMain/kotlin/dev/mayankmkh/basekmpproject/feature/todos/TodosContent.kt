package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import base_kmp_project.feature.todos.generated.resources.Res
import base_kmp_project.feature.todos.generated.resources.cancel
import base_kmp_project.feature.todos.generated.resources.completed
import base_kmp_project.feature.todos.generated.resources.create
import base_kmp_project.feature.todos.generated.resources.delete
import base_kmp_project.feature.todos.generated.resources.delete_todo_question
import base_kmp_project.feature.todos.generated.resources.hide_completed
import base_kmp_project.feature.todos.generated.resources.input_invalid
import base_kmp_project.feature.todos.generated.resources.nothing_here_yet
import base_kmp_project.feature.todos.generated.resources.owner
import base_kmp_project.feature.todos.generated.resources.refresh
import base_kmp_project.feature.todos.generated.resources.save
import base_kmp_project.feature.todos.generated.resources.sort
import base_kmp_project.feature.todos.generated.resources.sort_by_id
import base_kmp_project.feature.todos.generated.resources.sort_by_title
import base_kmp_project.feature.todos.generated.resources.title
import base_kmp_project.feature.todos.generated.resources.todo_unavailable
import base_kmp_project.feature.todos.generated.resources.todos
import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import dev.mayankmkh.basekmpproject.ui.designsystem.Centred
import dev.mayankmkh.basekmpproject.ui.designsystem.Failure
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoListContent(
    state: TodoListState,
    onAction: (TodoListAction) -> Unit,
    summary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = { TodoListTopBar(sortExpanded, { sortExpanded = it }, onAction) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAction(TodoListAction.Create) }) { Text("+") }
        },
    ) { padding ->
        TodoListBody(state, onAction, summary, Modifier.padding(padding))
    }
    if (state.pendingDelete != null) {
        DeleteConfirmation(
            onConfirm = { onAction(TodoListAction.ConfirmDelete) },
            onDismiss = { onAction(TodoListAction.DismissDelete) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListTopBar(
    sortExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAction: (TodoListAction) -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.todos)) },
        actions = {
            TextButton(onClick = { onExpandedChange(true) }) {
                Text(stringResource(Res.string.sort))
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { onExpandedChange(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.sort_by_id)) },
                    onClick = {
                        onExpandedChange(false)
                        onAction(TodoListAction.SetSort(TodoSort.ID))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.sort_by_title)) },
                    onClick = {
                        onExpandedChange(false)
                        onAction(TodoListAction.SetSort(TodoSort.TITLE))
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListBody(
    state: TodoListState,
    onAction: (TodoListAction) -> Unit,
    summary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(TodoListAction.Refresh) },
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when {
            state.isInitialLoading ->
                Centred(Modifier.fillMaxSize()) { CircularProgressIndicator() }
            state.problem != null && state.todos.isEmpty() ->
                Failure(
                    state.problem,
                    { onAction(TodoListAction.Refresh) },
                    Modifier.fillMaxSize(),
                )
            else ->
                LazyColumn {
                    item {
                        summary()
                        FilterChip(
                            selected = state.settings.hideCompleted,
                            onClick = {
                                onAction(
                                    TodoListAction.SetHideCompleted(!state.settings.hideCompleted)
                                )
                            },
                            label = { Text(stringResource(Res.string.hide_completed)) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        if (state.todos.isEmpty()) {
                            Text(
                                stringResource(Res.string.nothing_here_yet),
                                Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(state.todos, key = { it.id.value }) { todo ->
                        TodoRow(todo, todo.id !in state.mutatingIds, onAction)
                    }
                }
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, enabled: Boolean, onAction: (TodoListAction) -> Unit) {
    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(enabled = enabled) { onAction(TodoListAction.Open(todo.id)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = todo.completed,
                onCheckedChange = {
                    onAction(TodoListAction.SetCompleted(todo.id, completed = it))
                },
                enabled = enabled,
            )
            Text(todo.title, Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(
                onClick = { onAction(TodoListAction.RequestDelete(todo.id)) },
                enabled = enabled,
            ) {
                Text(stringResource(Res.string.delete))
            }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun TodoDetailContent(
    state: TodoDetailState,
    onAction: (TodoDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.isInitialLoading -> CircularProgressIndicator()
            state.todo != null -> {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onAction(TodoDetailAction.TitleChanged(it)) },
                    label = { Text(stringResource(Res.string.title)) },
                    supportingText = { FieldViolation(state.violations, TodoField.TITLE) },
                    isError = state.violations.any { it.field == TodoField.TITLE },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.todo.completed,
                        onCheckedChange = { onAction(TodoDetailAction.SetCompleted(it)) },
                        enabled = !state.isSubmitting,
                    )
                    Text(stringResource(Res.string.completed))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction(TodoDetailAction.Rename) },
                        enabled = !state.isSubmitting,
                    ) {
                        Text(stringResource(Res.string.save))
                    }
                    TextButton(
                        onClick = { onAction(TodoDetailAction.RequestDelete) },
                        enabled = !state.isSubmitting,
                    ) {
                        Text(stringResource(Res.string.delete))
                    }
                    TextButton(onClick = { onAction(TodoDetailAction.Refresh) }) {
                        Text(stringResource(Res.string.refresh))
                    }
                }
            }
            state.problem != null -> Failure(state.problem, { onAction(TodoDetailAction.Refresh) })
            state.isAbsent -> Centred { Text(stringResource(Res.string.todo_unavailable)) }
        }
    }
    if (state.confirmDelete) {
        DeleteConfirmation(
            onConfirm = { onAction(TodoDetailAction.ConfirmDelete) },
            onDismiss = { onAction(TodoDetailAction.DismissDelete) },
        )
    }
}

@Composable
internal fun TodoEditorContent(
    state: TodoEditorState,
    onAction: (TodoEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FormViolations(state.violations)
        OutlinedTextField(
            value = state.title,
            onValueChange = { onAction(TodoEditorAction.TitleChanged(it)) },
            label = { Text(stringResource(Res.string.title)) },
            supportingText = { FieldViolation(state.violations, TodoField.TITLE) },
            isError = state.violations.any { it.field == TodoField.TITLE },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.ownerId,
            onValueChange = { onAction(TodoEditorAction.OwnerChanged(it)) },
            label = { Text(stringResource(Res.string.owner)) },
            supportingText = { FieldViolation(state.violations, TodoField.OWNER_ID) },
            isError = state.violations.any { it.field == TodoField.OWNER_ID },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction(TodoEditorAction.Submit) },
            enabled = !state.isSubmitting,
        ) {
            if (state.isSubmitting) CircularProgressIndicator()
            else Text(stringResource(Res.string.create))
        }
    }
}

@Composable
private fun DeleteConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_todo_question)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun FormViolations(violations: List<Violation<TodoField>>) {
    violations
        .filter { it.field == null }
        .forEach { violation ->
            Text(
                violation.message ?: stringResource(Res.string.input_invalid),
                color = MaterialTheme.colorScheme.error,
            )
        }
}

@Composable
private fun FieldViolation(violations: List<Violation<TodoField>>, field: TodoField) {
    violations
        .firstOrNull { it.field == field }
        ?.let { violation ->
            Text(violation.message ?: stringResource(violation.code.messageResource()))
        }
}
