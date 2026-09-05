package dev.mayankmkh.basekmpproject.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.DeleteTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoList
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoViolation
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoSummaryOutput
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.foundation.resource.failure
import dev.mayankmkh.basekmpproject.foundation.resource.hasValue
import dev.mayankmkh.basekmpproject.foundation.resource.isRefreshing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class TodoListState(
    val todos: List<Todo> = emptyList(),
    val settings: TodoSettings = TodoSettings(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val problem: ResourceProblem? = null,
    val pendingDelete: TodoId? = null,
    val mutatingIds: Set<TodoId> = emptySet(),
)

internal sealed interface TodoListAction {
    data object Refresh : TodoListAction

    data class Open(val id: TodoId) : TodoListAction

    data object Create : TodoListAction

    data class SetCompleted(val id: TodoId, val completed: Boolean) : TodoListAction

    data class RequestDelete(val id: TodoId) : TodoListAction

    data object DismissDelete : TodoListAction

    data object ConfirmDelete : TodoListAction

    data class SetHideCompleted(val hideCompleted: Boolean) : TodoListAction

    data class SetSort(val sort: TodoSort) : TodoListAction
}

internal sealed interface TodosUiCommand {
    data class ShowFailure(val category: ResourceProblemCategory) : TodosUiCommand
}

internal class TodoListViewModel(
    instanceKey: FeatureInstanceKey,
    queries: TodosQueries,
    private val commands: TodosCommands,
) : ViewModel() {
    private val pendingDelete = MutableStateFlow<TodoId?>(null)
    private val mutatingIds = MutableStateFlow(emptySet<TodoId>())
    private val uiCommandChannel = Channel<TodosUiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<TodoListOutput>(Channel.BUFFERED)

    val uiCommands: Flow<TodosUiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<TodoListOutput> = outputChannel.receiveAsFlow()
    val state: StateFlow<TodoListState> =
        combine(queries.observeTodos(), queries.observeSettings(), pendingDelete, mutatingIds) {
                observation,
                settings,
                deleting,
                mutating ->
                observation.toListState(settings, deleting, mutating)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListState())

    init {
        require(instanceKey.value.isNotBlank())
    }

    fun onAction(action: TodoListAction) {
        when (action) {
            TodoListAction.Refresh -> refresh()
            is TodoListAction.Open -> emit(TodoListOutput.OpenDetail(action.id))
            TodoListAction.Create -> emit(TodoListOutput.OpenEditor)
            is TodoListAction.SetCompleted -> setCompleted(action.id, action.completed)
            is TodoListAction.RequestDelete -> pendingDelete.value = action.id
            TodoListAction.DismissDelete -> pendingDelete.value = null
            TodoListAction.ConfirmDelete -> deletePending()
            is TodoListAction.SetHideCompleted ->
                updateSettings(state.value.settings.copy(hideCompleted = action.hideCompleted))
            is TodoListAction.SetSort ->
                updateSettings(state.value.settings.copy(sort = action.sort))
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val outcome = commands.refreshTodos()
            if (outcome is RefreshOutcome.Failed) show(outcome.problem.category)
        }
    }

    private fun setCompleted(id: TodoId, completed: Boolean) {
        mutate(id) { handleUpdate(commands.setCompleted(id, completed)) }
    }

    private fun deletePending() {
        val id = pendingDelete.value ?: return
        pendingDelete.value = null
        mutate(id) {
            when (val result = commands.deleteTodo(id)) {
                DeleteTodoResult.Deleted -> Unit
                DeleteTodoResult.NotFound -> show(ResourceProblemCategory.PERMANENT)
                is DeleteTodoResult.Failed -> show(result.problem.category)
            }
        }
    }

    /**
     * A list row has no form to pin violations to and nowhere to navigate when the server no longer
     * knows the row, so both read as a transient failure; the durable list reconciles on its own.
     */
    private suspend fun handleUpdate(result: UpdateTodoResult) {
        when (result) {
            UpdateTodoResult.Updated -> Unit
            UpdateTodoResult.NotFound,
            is UpdateTodoResult.InvalidInput -> show(ResourceProblemCategory.PERMANENT)
            is UpdateTodoResult.Failed -> show(result.problem.category)
        }
    }

    private fun updateSettings(settings: TodoSettings) {
        viewModelScope.launch { commands.updateSettings(settings) }
    }

    private fun mutate(id: TodoId, block: suspend () -> Unit) {
        viewModelScope.launch {
            mutatingIds.value += id
            try {
                block()
            } finally {
                mutatingIds.value -= id
            }
        }
    }

    private fun emit(output: TodoListOutput) {
        viewModelScope.launch { outputChannel.send(output) }
    }

    private suspend fun show(category: ResourceProblemCategory) {
        uiCommandChannel.send(TodosUiCommand.ShowFailure(category))
    }
}

internal data class TodoDetailState(
    val todo: Todo? = null,
    val title: String = "",
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val problem: ResourceProblem? = null,
    val violations: List<TodoViolation> = emptyList(),
    val confirmDelete: Boolean = false,
)

internal sealed interface TodoDetailAction {
    data object Back : TodoDetailAction

    data object Refresh : TodoDetailAction

    data class TitleChanged(val title: String) : TodoDetailAction

    data object Rename : TodoDetailAction

    data class SetCompleted(val completed: Boolean) : TodoDetailAction

    data object RequestDelete : TodoDetailAction

    data object DismissDelete : TodoDetailAction

    data object ConfirmDelete : TodoDetailAction
}

internal class TodoDetailViewModel(
    val todoId: TodoId,
    instanceKey: FeatureInstanceKey,
    queries: TodosQueries,
    private val commands: TodosCommands,
) : ViewModel() {
    /** The user's unsaved title, or null while the field mirrors the stored todo. */
    private val draft = MutableStateFlow<String?>(null)
    private val submitting = MutableStateFlow(false)
    private val violations = MutableStateFlow(emptyList<TodoViolation>())
    private val confirmDelete = MutableStateFlow(false)
    private val uiCommandChannel = Channel<TodosUiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<TodoDetailOutput>(Channel.BUFFERED)

    val uiCommands: Flow<TodosUiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<TodoDetailOutput> = outputChannel.receiveAsFlow()
    val state: StateFlow<TodoDetailState> =
        combine(
                // Observed inside the state pipeline so the resource stays subscriber-gated: a
                // hidden detail entry costs nothing until its screen is shown again.
                queries.observeTodo(todoId).onEach { current ->
                    if (
                        current.value == null &&
                            current.failure?.category == ResourceProblemCategory.PERMANENT
                    ) {
                        outputChannel.send(TodoDetailOutput.NotFound(todoId))
                    }
                },
                draft,
                submitting,
                violations,
                confirmDelete,
            ) { current, draftTitle, isSubmitting, currentViolations, deleting ->
                current.toDetailState(draftTitle, isSubmitting, currentViolations, deleting)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoDetailState())

    init {
        require(instanceKey.value.isNotBlank())
    }

    fun onAction(action: TodoDetailAction) {
        when (action) {
            TodoDetailAction.Back -> emit(TodoDetailOutput.Back)
            TodoDetailAction.Refresh -> refresh()
            is TodoDetailAction.TitleChanged -> draft.value = action.title
            TodoDetailAction.Rename -> rename()
            is TodoDetailAction.SetCompleted -> setCompleted(action.completed)
            TodoDetailAction.RequestDelete -> confirmDelete.value = true
            TodoDetailAction.DismissDelete -> confirmDelete.value = false
            TodoDetailAction.ConfirmDelete -> delete()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val outcome = commands.refreshTodo(todoId)
            if (outcome is RefreshOutcome.Failed) show(outcome.problem)
        }
    }

    private fun rename() {
        submit {
            val title = draft.value ?: state.value.title
            if (handleUpdate(commands.renameTodo(todoId, title))) draft.value = null
        }
    }

    private fun setCompleted(completed: Boolean) {
        submit { handleUpdate(commands.setCompleted(todoId, completed)) }
    }

    /** Applies one update result to the screen; true when the todo was updated. */
    private suspend fun handleUpdate(result: UpdateTodoResult): Boolean {
        when (result) {
            UpdateTodoResult.Updated -> violations.value = emptyList()
            UpdateTodoResult.NotFound -> emitNow(TodoDetailOutput.NotFound(todoId))
            is UpdateTodoResult.InvalidInput -> violations.value = result.violations
            is UpdateTodoResult.Failed -> show(result.problem)
        }
        return result == UpdateTodoResult.Updated
    }

    private fun delete() {
        confirmDelete.value = false
        submit {
            when (val result = commands.deleteTodo(todoId)) {
                DeleteTodoResult.Deleted -> emitNow(TodoDetailOutput.Deleted(todoId))
                DeleteTodoResult.NotFound -> emitNow(TodoDetailOutput.NotFound(todoId))
                is DeleteTodoResult.Failed -> show(result.problem)
            }
        }
    }

    private fun submit(block: suspend () -> Unit) {
        viewModelScope.launch {
            submitting.value = true
            try {
                block()
            } finally {
                submitting.value = false
            }
        }
    }

    private fun emit(output: TodoDetailOutput) {
        viewModelScope.launch { emitNow(output) }
    }

    private suspend fun emitNow(output: TodoDetailOutput) {
        outputChannel.send(output)
    }

    private suspend fun show(problem: ResourceProblem) {
        uiCommandChannel.send(TodosUiCommand.ShowFailure(problem.category))
    }
}

internal data class TodoEditorState(
    val title: String = "",
    val ownerId: String = "1",
    val isSubmitting: Boolean = false,
    val violations: List<TodoViolation> = emptyList(),
)

internal sealed interface TodoEditorAction {
    data object Back : TodoEditorAction

    data class TitleChanged(val title: String) : TodoEditorAction

    data class OwnerChanged(val ownerId: String) : TodoEditorAction

    data object Submit : TodoEditorAction
}

internal class TodoEditorViewModel(
    instanceKey: FeatureInstanceKey,
    private val commands: TodosCommands,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TodoEditorState())
    private val uiCommandChannel = Channel<TodosUiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<TodoEditorOutput>(Channel.BUFFERED)

    val state: StateFlow<TodoEditorState> = mutableState
    val uiCommands: Flow<TodosUiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<TodoEditorOutput> = outputChannel.receiveAsFlow()

    init {
        require(instanceKey.value.isNotBlank())
    }

    fun onAction(action: TodoEditorAction) {
        when (action) {
            TodoEditorAction.Back -> emit(TodoEditorOutput.Back)
            is TodoEditorAction.TitleChanged ->
                mutableState.value = mutableState.value.copy(title = action.title)
            is TodoEditorAction.OwnerChanged ->
                mutableState.value = mutableState.value.copy(ownerId = action.ownerId)
            TodoEditorAction.Submit -> submit()
        }
    }

    private fun submit() {
        if (mutableState.value.isSubmitting) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSubmitting = true)
            val current = mutableState.value
            when (
                val result =
                    commands.createTodo(
                        TodoDraft(current.title, current.ownerId.toLongOrNull() ?: 0)
                    )
            ) {
                is CreateTodoResult.Created -> emitNow(TodoEditorOutput.Created(result.id))
                is CreateTodoResult.InvalidInput ->
                    mutableState.value = mutableState.value.copy(violations = result.violations)
                is CreateTodoResult.Failed -> show(result.problem)
            }
            mutableState.value = mutableState.value.copy(isSubmitting = false)
        }
    }

    private fun emit(output: TodoEditorOutput) {
        viewModelScope.launch { emitNow(output) }
    }

    private suspend fun emitNow(output: TodoEditorOutput) {
        outputChannel.send(output)
    }

    private suspend fun show(problem: ResourceProblem) {
        uiCommandChannel.send(TodosUiCommand.ShowFailure(problem.category))
    }
}

internal data class TodoSummaryState(
    val open: Int = 0,
    val completed: Int = 0,
    val isLoading: Boolean = true,
)

internal sealed interface TodoSummaryAction {
    data object OpenTodos : TodoSummaryAction
}

internal class TodoSummaryViewModel(
    instanceKey: FeatureInstanceKey,
    queries: TodosQueries,
) : ViewModel() {
    private val outputChannel = Channel<TodoSummaryOutput>(Channel.BUFFERED)
    val outputs: Flow<TodoSummaryOutput> = outputChannel.receiveAsFlow()
    val state: StateFlow<TodoSummaryState> =
        queries
            .observeTodos()
            .map { observation ->
                TodoSummaryState(
                    open = observation.value?.todos?.count { !it.completed } ?: 0,
                    completed = observation.value?.todos?.count(Todo::completed) ?: 0,
                    isLoading = !observation.hasValue,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoSummaryState())

    init {
        require(instanceKey.value.isNotBlank())
    }

    fun onAction(action: TodoSummaryAction) {
        when (action) {
            TodoSummaryAction.OpenTodos ->
                viewModelScope.launch { outputChannel.send(TodoSummaryOutput.OpenTodos) }
        }
    }
}

private fun ResourceObservation<TodoList>.toListState(
    settings: TodoSettings,
    pendingDelete: TodoId?,
    mutatingIds: Set<TodoId>,
) =
    TodoListState(
        todos = value?.todos.orEmpty(),
        settings = settings,
        isInitialLoading = !hasValue && isRefreshing,
        isRefreshing = hasValue && isRefreshing,
        problem = failure,
        pendingDelete = pendingDelete,
        mutatingIds = mutatingIds,
    )

private fun ResourceObservation<Todo>.toDetailState(
    draftTitle: String?,
    isSubmitting: Boolean,
    violations: List<TodoViolation>,
    confirmDelete: Boolean,
) =
    TodoDetailState(
        todo = value,
        title = draftTitle ?: value?.title.orEmpty(),
        isInitialLoading = !hasValue && isRefreshing,
        isRefreshing = hasValue && isRefreshing,
        isSubmitting = isSubmitting,
        problem = failure,
        violations = violations,
        confirmDelete = confirmDelete,
    )
