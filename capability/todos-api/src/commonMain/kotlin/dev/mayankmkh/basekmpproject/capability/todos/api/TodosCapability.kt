package dev.mayankmkh.basekmpproject.capability.todos.api

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import kotlinx.coroutines.flow.Flow

public interface TodosQueries {
    /** The durable list, already filtered and sorted according to [observeSettings]. */
    public fun observeTodos(): Flow<ResourceObservation<TodoList>>

    public fun observeTodo(id: TodoId): Flow<ResourceObservation<Todo>>

    public fun observeSettings(): Flow<TodoSettings>
}

public interface TodosCommands {
    public suspend fun refreshTodos(qos: RefreshQos = RefreshQos.visible()): RefreshOutcome

    public suspend fun refreshTodo(
        id: TodoId,
        qos: RefreshQos = RefreshQos.visible(),
    ): RefreshOutcome

    public suspend fun createTodo(draft: TodoDraft): CreateTodoResult

    public suspend fun setCompleted(id: TodoId, completed: Boolean): UpdateTodoResult

    public suspend fun renameTodo(id: TodoId, title: String): UpdateTodoResult

    public suspend fun deleteTodo(id: TodoId): DeleteTodoResult

    public suspend fun updateSettings(settings: TodoSettings)
}

public sealed interface CreateTodoResult {
    public data class Created(val id: TodoId) : CreateTodoResult

    public data class InvalidInput(val violations: List<TodoViolation>) : CreateTodoResult

    public data class Failed(val problem: ResourceProblem) : CreateTodoResult
}

public sealed interface UpdateTodoResult {
    public data object Updated : UpdateTodoResult

    public data object NotFound : UpdateTodoResult

    public data class InvalidInput(val violations: List<TodoViolation>) : UpdateTodoResult

    public data class Failed(val problem: ResourceProblem) : UpdateTodoResult
}

public sealed interface DeleteTodoResult {
    public data object Deleted : DeleteTodoResult

    public data object NotFound : DeleteTodoResult

    public data class Failed(val problem: ResourceProblem) : DeleteTodoResult
}
