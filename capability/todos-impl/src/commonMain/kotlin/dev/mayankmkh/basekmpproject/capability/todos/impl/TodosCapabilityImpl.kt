package dev.mayankmkh.basekmpproject.capability.todos.impl

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.DeleteTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoList
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoViolation
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.SyncCoordinator
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.commit
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.observations
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class TodosCapabilityImpl(
    private val remoteSource: TodosRemoteSource,
    private val localSource: TodosLocalSource,
    private val settingsSource: TodosSettingsSource,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
) : TodosQueries, TodosCommands, AutoCloseable {
    private val scope = applicationRuntimeScope.childScope("todos")
    private val listSync =
        SyncCoordinator<Unit>(
            scope,
            sync = { _, _ -> syncTodos(remoteSource, localSource) },
            retryTriggers = connectivityMonitor.reconnects(),
        )
    private val itemSync =
        SyncCoordinator<TodoId>(
            scope,
            sync = { id, _ -> syncTodo(remoteSource, localSource, id) },
            retryTriggers = connectivityMonitor.reconnects(),
        )

    override fun observeTodos(): Flow<ResourceObservation<TodoList>> =
        listSync.observations(
            Unit,
            settingsSource.observe().flatMapLatest { settings ->
                combine(localSource.observeTodos(settings), localSource.observeInitialized()) {
                    todos,
                    initialized ->
                    if (initialized) TodoList(todos.map(TodoEntity::toTodo)) else null
                }
            },
        )

    override fun observeTodo(id: TodoId): Flow<ResourceObservation<Todo>> =
        itemSync.observations(id, localSource.observeById(id).map { it?.toTodo() })

    override fun observeSettings(): Flow<TodoSettings> = settingsSource.observe()

    override suspend fun refreshTodos(qos: RefreshQos): RefreshOutcome = listSync.sync(Unit, qos)

    override suspend fun refreshTodo(id: TodoId, qos: RefreshQos): RefreshOutcome =
        itemSync.sync(id, qos)

    override suspend fun createTodo(draft: TodoDraft): CreateTodoResult {
        val violations = validate(draft.title, draft.ownerId)
        if (violations.isNotEmpty()) return CreateTodoResult.InvalidInput(violations)

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
        return remoteSource
            .createTodo(draft.copy(title = optimistic.title))
            .fold(
                success = { response ->
                    val confirmed = response.toEntity(id = id, localCreated = 1)
                    if (confirmed != optimistic) localSource.upsert(confirmed)
                    CreateTodoResult.Created(id)
                },
                failure = { failure ->
                    localSource.delete(id)
                    when (failure) {
                        is TodoRemoteFailure.InvalidInput ->
                            CreateTodoResult.InvalidInput(failure.violations)
                        is TodoRemoteFailure.NotFound,
                        is TodoRemoteFailure.Infrastructure ->
                            CreateTodoResult.Failed(failure.problem)
                    }
                },
            )
    }

    override suspend fun setCompleted(id: TodoId, completed: Boolean): UpdateTodoResult =
        updateTodo(localSource, id, transform = { it.copy(completed = completed.toLong()) }) {
            remoteSource.setCompleted(id.value, completed)
        }

    override suspend fun renameTodo(id: TodoId, title: String): UpdateTodoResult {
        val violations = validateTitle(title)
        if (violations.isNotEmpty()) return UpdateTodoResult.InvalidInput(violations)
        val trimmed = title.trim()
        return updateTodo(localSource, id, transform = { it.copy(title = trimmed) }) {
            remoteSource.renameTodo(id.value, trimmed)
        }
    }

    override suspend fun deleteTodo(id: TodoId): DeleteTodoResult {
        val previous = localSource.find(id) ?: return DeleteTodoResult.NotFound
        localSource.delete(id)
        return remoteSource
            .deleteTodo(id.value)
            .fold(
                success = { DeleteTodoResult.Deleted },
                failure = { failure ->
                    localSource.upsert(previous)
                    when (failure) {
                        is TodoRemoteFailure.NotFound -> DeleteTodoResult.NotFound
                        is TodoRemoteFailure.InvalidInput,
                        is TodoRemoteFailure.Infrastructure ->
                            DeleteTodoResult.Failed(failure.problem)
                    }
                },
            )
    }

    override suspend fun updateSettings(settings: TodoSettings) {
        settingsSource.update(settings)
    }

    override fun close() {
        scope.cancel()
    }
}

private fun validate(title: String, ownerId: Long): List<TodoViolation> =
    validateTitle(title) +
        if (ownerId <= 0) {
            listOf(TodoViolation(TodoField.OWNER_ID, "invalid_owner", message = null))
        } else {
            emptyList()
        }

private fun validateTitle(title: String): List<TodoViolation> =
    when {
        title.isBlank() -> listOf(TodoViolation(TodoField.TITLE, "blank", message = null))
        title.length > MaxTitleLength ->
            listOf(TodoViolation(TodoField.TITLE, "too_long", message = null))
        else -> emptyList()
    }

private fun TodoDto.toEntity(
    id: TodoId = TodoId(this.id),
    localCreated: Long,
) = TodoEntity(id.value, userId, title, completed.toLong(), localCreated)

private fun TodoEntity.toTodo() = Todo(TodoId(id), ownerId, title, completed != 0L)

private suspend fun updateTodo(
    localSource: TodosLocalSource,
    id: TodoId,
    transform: (TodoEntity) -> TodoEntity,
    send: suspend () -> Result<TodoDto, TodoRemoteFailure>,
): UpdateTodoResult {
    val previous = localSource.find(id) ?: return UpdateTodoResult.NotFound
    val optimistic = transform(previous)
    localSource.upsert(optimistic)
    return send()
        .fold(
            success = { response ->
                // An echoing backend confirms exactly what was written; skip the no-op write and
                // the query re-runs it would trigger.
                val confirmed = response.toEntity(id, previous.localCreated)
                if (confirmed != optimistic) localSource.upsert(confirmed)
                UpdateTodoResult.Updated
            },
            failure = { failure ->
                localSource.upsert(previous)
                when (failure) {
                    is TodoRemoteFailure.NotFound -> UpdateTodoResult.NotFound
                    is TodoRemoteFailure.InvalidInput ->
                        UpdateTodoResult.InvalidInput(failure.violations)
                    is TodoRemoteFailure.Infrastructure -> UpdateTodoResult.Failed(failure.problem)
                }
            },
        )
}

private suspend fun syncTodos(
    remoteSource: TodosRemoteSource,
    localSource: TodosLocalSource,
): RefreshOutcome =
    remoteSource.getTodos().commit { todos ->
        localSource.replaceFromServer(todos.map { it.toEntity(localCreated = 0) })
    }

private suspend fun syncTodo(
    remoteSource: TodosRemoteSource,
    localSource: TodosLocalSource,
    id: TodoId,
): RefreshOutcome =
    remoteSource.getTodo(id.value).commit { remote ->
        val localCreated = localSource.find(id)?.localCreated ?: 0
        localSource.upsert(remote.toEntity(id, localCreated))
    }

private const val MaxTitleLength = 200
