package dev.mayankmkh.basekmpproject.capability.todos.impl

import co.touchlab.kermit.Logger
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.DeleteTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoList
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.CommandBridge
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.SyncCoordinator
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.observations
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class TodosCapabilityImpl(
    private val remoteSource: TodosRemoteSource,
    private val localSource: TodosLocalSource,
    private val settingsSource: TodosSettingsSource,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
    logger: Logger,
) : TodosQueries, TodosCommands, AutoCloseable {
    private val scope = applicationRuntimeScope.childScope("todos")
    private val bridge = CommandBridge(logger, "todos")
    private val listSync =
        SyncCoordinator<Unit>(
            scope,
            sync = { _, _ -> syncTodos(remoteSource, localSource, bridge) },
            retryTriggers = connectivityMonitor.reconnects(),
            bridge = bridge,
        )
    private val itemSync =
        SyncCoordinator<TodoId>(
            scope,
            sync = { id, _ -> syncTodo(remoteSource, localSource, bridge, id) },
            retryTriggers = connectivityMonitor.reconnects(),
            bridge = bridge,
        )

    override fun observeTodos(): Flow<ResourceObservation<TodoList>> =
        listSync.observations(
            Unit,
            combine(
                settingsSource
                    .observe()
                    .distinctUntilChanged()
                    .flatMapLatest(localSource::observeTodos),
                localSource.observeInitialized(),
            ) { todos, initialised ->
                if (initialised) TodoList(todos.map(TodoEntity::toTodo)) else null
            },
        )

    override fun observeTodo(id: TodoId): Flow<ResourceObservation<Todo>> =
        itemSync.observations(id, localSource.observeById(id).map { it?.toTodo() })

    override fun observeSettings(): Flow<TodoSettings> = settingsSource.observe()

    override suspend fun refreshTodos(qos: RefreshQos): Outcome<Unit> = listSync.sync(Unit, qos)

    override suspend fun refreshTodo(id: TodoId, qos: RefreshQos): Outcome<Unit> =
        itemSync.sync(id, qos)

    override suspend fun createTodo(draft: TodoDraft): Outcome<CreateTodoResult> {
        val violations = validate(draft.title, draft.ownerId)
        if (violations.isNotEmpty()) {
            return Outcome.Completed(CreateTodoResult.InvalidInput(violations))
        }

        val id = localSource.allocateLocalId()
        val optimistic =
            TodoEntity(
                id.value,
                draft.ownerId,
                draft.title.trim(),
                completed = 0,
                localCreated = 1,
            )
        localSource.upsert(optimistic)
        return bridge.toOutcome(
            remoteSource.createTodo(draft.copy(title = optimistic.title)),
            operation = "create",
            onFailure = { localSource.delete(id) },
        ) { answer ->
            when (answer) {
                is CreateTodoRemoteAnswer.Created -> {
                    val confirmed = answer.todo.toEntity(id = id, localCreated = 1)
                    if (confirmed != optimistic) localSource.upsert(confirmed)
                    CreateTodoResult.Created(id)
                }
                is CreateTodoRemoteAnswer.InvalidInput -> {
                    localSource.delete(id)
                    CreateTodoResult.InvalidInput(answer.violations)
                }
            }
        }
    }

    override suspend fun setCompleted(
        id: TodoId,
        completed: Boolean,
    ): Outcome<UpdateTodoResult> =
        localSource.updateTodo(
            id,
            "complete",
            bridge,
            transform = { it.copy(completed = completed.toLong()) },
        ) {
            remoteSource.setCompleted(id.value, completed)
        }

    override suspend fun renameTodo(id: TodoId, title: String): Outcome<UpdateTodoResult> {
        val violations = validateTitle(title)
        if (violations.isNotEmpty()) {
            return Outcome.Completed(UpdateTodoResult.InvalidInput(violations))
        }
        val trimmed = title.trim()
        return localSource.updateTodo(
            id,
            "rename",
            bridge,
            transform = { it.copy(title = trimmed) },
        ) {
            remoteSource.renameTodo(id.value, trimmed)
        }
    }

    override suspend fun deleteTodo(id: TodoId): Outcome<DeleteTodoResult> {
        val previous = localSource.find(id) ?: return Outcome.Completed(DeleteTodoResult.NotFound)
        localSource.delete(id)
        return bridge.toOutcome(
            remoteSource.deleteTodo(id.value),
            operation = "delete",
            onFailure = { localSource.upsert(previous) },
        ) { answer ->
            when (answer) {
                DeleteTodoRemoteAnswer.Deleted -> DeleteTodoResult.Deleted
                DeleteTodoRemoteAnswer.NotFound -> DeleteTodoResult.NotFound
            }
        }
    }

    override suspend fun updateSettings(settings: TodoSettings): Outcome<Unit> {
        settingsSource.update(settings)
        return Outcome.Completed(Unit)
    }

    override fun close() {
        scope.cancel()
    }
}

private suspend fun TodosLocalSource.updateTodo(
    id: TodoId,
    operation: String,
    bridge: CommandBridge,
    transform: (TodoEntity) -> TodoEntity,
    send: suspend () -> Result<UpdateTodoRemoteAnswer, NetworkFailure>,
): Outcome<UpdateTodoResult> {
    val previous = find(id) ?: return Outcome.Completed(UpdateTodoResult.NotFound)
    val optimistic = transform(previous)
    upsert(optimistic)
    return bridge.toOutcome(
        send(),
        operation = operation,
        onFailure = { upsert(previous) },
    ) { answer ->
        when (answer) {
            is UpdateTodoRemoteAnswer.Updated -> {
                val confirmed = answer.todo.toEntity(id, previous.localCreated)
                if (confirmed != optimistic) upsert(confirmed)
                UpdateTodoResult.Updated
            }
            UpdateTodoRemoteAnswer.NotFound -> {
                delete(id)
                UpdateTodoResult.NotFound
            }
            is UpdateTodoRemoteAnswer.InvalidInput -> {
                upsert(previous)
                UpdateTodoResult.InvalidInput(answer.violations)
            }
        }
    }
}

private suspend fun syncTodos(
    remoteSource: TodosRemoteSource,
    localSource: TodosLocalSource,
    bridge: CommandBridge,
): Outcome<Unit> =
    bridge.commit(remoteSource.getTodos(), "list.refresh") { todos ->
        localSource.replaceFromServer(todos.map { it.toEntity(localCreated = 0) })
    }

private suspend fun syncTodo(
    remoteSource: TodosRemoteSource,
    localSource: TodosLocalSource,
    bridge: CommandBridge,
    id: TodoId,
): Outcome<Unit> =
    bridge.commit(remoteSource.getTodo(id.value), "detail.refresh") { todo ->
        if (todo == null) {
            localSource.delete(id)
        } else {
            val localCreated = localSource.find(id)?.localCreated ?: 0
            localSource.upsert(todo.toEntity(id, localCreated))
        }
    }

private fun validate(title: String, ownerId: Long): List<Violation<TodoField>> =
    validateTitle(title) +
        if (ownerId <= 0) {
            listOf(Violation(TodoField.OWNER_ID, "invalid_owner"))
        } else {
            emptyList()
        }

private fun validateTitle(title: String): List<Violation<TodoField>> =
    when {
        title.isBlank() -> listOf(Violation(TodoField.TITLE, "blank"))
        title.length > MaxTitleLength -> listOf(Violation(TodoField.TITLE, "too_long"))
        else -> emptyList()
    }

private fun TodoDto.toEntity(
    id: TodoId = TodoId(this.id),
    localCreated: Long,
) = TodoEntity(id.value, userId, title, completed.toLong(), localCreated)

private fun TodoEntity.toTodo() = Todo(TodoId(id), ownerId, title, completed != 0L)

private const val MaxTitleLength = 200
