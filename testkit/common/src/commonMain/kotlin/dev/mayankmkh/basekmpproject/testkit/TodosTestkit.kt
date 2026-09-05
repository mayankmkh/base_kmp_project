package dev.mayankmkh.basekmpproject.testkit

import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.DeleteTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoList
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

object TodosFixtures {
    fun todo(
        id: Long = 1,
        ownerId: Long = 1,
        title: String = "Todo $id",
        completed: Boolean = false,
    ): Todo = Todo(TodoId(id), ownerId, title, completed)

    fun todos(): List<Todo> =
        listOf(
            todo(1, title = "Buy groceries"),
            todo(2, title = "Write release notes", completed = true),
        )
}

class FakeTodosQueries(
    list: ResourceObservation<TodoList> =
        ResourceObservationFixtures.idle(TodoList(TodosFixtures.todos())),
    settings: TodoSettings = TodoSettings(),
) : TodosQueries {
    val todos = MutableStateFlow(list)
    val settings = MutableStateFlow(settings)
    val todoFlows =
        TodosFixtures.todos()
            .associate { todo ->
                todo.id to MutableStateFlow(ResourceObservationFixtures.idle(todo))
            }
            .toMutableMap()
    var listObserverCount: Int = 0
        private set

    override fun observeTodos(): Flow<ResourceObservation<TodoList>> {
        listObserverCount++
        return todos
    }

    override fun observeTodo(id: TodoId): Flow<ResourceObservation<Todo>> {
        return todoFlows.getOrPut(id) { MutableStateFlow(ResourceObservation.initial()) }
    }

    override fun observeSettings(): Flow<TodoSettings> = settings
}

class FakeTodosCommands : TodosCommands {
    var onRefreshTodos: suspend (RefreshQos) -> Outcome<Unit> = { Outcome.Completed(Unit) }
    var onRefreshTodo: suspend (TodoId, RefreshQos) -> Outcome<Unit> = { _, _ ->
        Outcome.Completed(Unit)
    }
    var onCreate: suspend (TodoDraft) -> Outcome<CreateTodoResult> = {
        Outcome.Completed(CreateTodoResult.Created(TodoId(1_000_000)))
    }
    var onSetCompleted: suspend (TodoId, Boolean) -> Outcome<UpdateTodoResult> = { _, _ ->
        Outcome.Completed(UpdateTodoResult.Updated)
    }
    var onRename: suspend (TodoId, String) -> Outcome<UpdateTodoResult> = { _, _ ->
        Outcome.Completed(UpdateTodoResult.Updated)
    }
    var onDelete: suspend (TodoId) -> Outcome<DeleteTodoResult> = {
        Outcome.Completed(DeleteTodoResult.Deleted)
    }

    val completedChanges = mutableListOf<Pair<TodoId, Boolean>>()
    val renames = mutableListOf<Pair<TodoId, String>>()
    val deletes = mutableListOf<TodoId>()
    val settingsUpdates = mutableListOf<TodoSettings>()

    override suspend fun refreshTodos(qos: RefreshQos): Outcome<Unit> = onRefreshTodos(qos)

    override suspend fun refreshTodo(id: TodoId, qos: RefreshQos): Outcome<Unit> =
        onRefreshTodo(id, qos)

    override suspend fun createTodo(draft: TodoDraft): Outcome<CreateTodoResult> = onCreate(draft)

    override suspend fun setCompleted(id: TodoId, completed: Boolean): Outcome<UpdateTodoResult> {
        completedChanges += id to completed
        return onSetCompleted(id, completed)
    }

    override suspend fun renameTodo(id: TodoId, title: String): Outcome<UpdateTodoResult> {
        renames += id to title
        return onRename(id, title)
    }

    override suspend fun deleteTodo(id: TodoId): Outcome<DeleteTodoResult> {
        deletes += id
        return onDelete(id)
    }

    override suspend fun updateSettings(settings: TodoSettings): Outcome<Unit> {
        settingsUpdates += settings
        return Outcome.Completed(Unit)
    }
}
