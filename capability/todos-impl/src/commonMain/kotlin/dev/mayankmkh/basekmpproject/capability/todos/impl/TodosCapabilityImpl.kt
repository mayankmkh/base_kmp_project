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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
        val answer =
            localSource.revertingOnCancel(id, expected = optimistic, previous = null) {
                localSource.upsert(optimistic)
                remoteSource.createTodo(draft.copy(title = optimistic.title))
            }
        return bridge.toOutcome(
            answer,
            operation = "create",
            onFailure = { withContext(NonCancellable) { localSource.delete(id) } },
        ) { answer ->
            when (answer) {
                is CreateTodoRemoteAnswer.Created -> {
                    val confirmed = answer.todo.toEntity(id = id, localCreated = 1)
                    if (confirmed != optimistic) localSource.upsert(confirmed)
                    CreateTodoResult.Created(id)
                }
                is CreateTodoRemoteAnswer.InvalidInput -> {
                    withContext(NonCancellable) { localSource.delete(id) }
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
        val answer =
            localSource.revertingOnCancel(id, expected = null, previous = previous) {
                localSource.delete(id)
                remoteSource.deleteTodo(id.value)
            }
        return bridge.toOutcome(
            answer,
            operation = "delete",
            onFailure = { withContext(NonCancellable) { localSource.upsert(previous) } },
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
    val answer =
        revertingOnCancel(id, expected = optimistic, previous = previous) {
            upsert(optimistic)
            send()
        }
    return bridge.toOutcome(
        answer,
        operation = operation,
        onFailure = { withContext(NonCancellable) { upsert(previous) } },
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
                withContext(NonCancellable) { upsert(previous) }
                UpdateTodoResult.InvalidInput(answer.violations)
            }
        }
    }
}

/**
 * Runs an optimistic command's local write and request and, if the caller is cancelled before an
 * answer, puts the row back as [previous] (no row when null), reconciling the unconfirmed write as
 * a failure would.
 *
 * The optimistic write belongs inside [send]: its `withContext` onto the database lane can hand
 * back a cancellation after the row was committed, and a write outside this guard would then stay
 * behind. When the write never landed, the row is not [expected] and the revert does nothing.
 *
 * Only while the row is still exactly [expected], the optimistic value this command wrote (null: no
 * row), checked and replaced in one transaction: a newer edit made meanwhile is the user's latest
 * word and is left alone. For a create, [expected] is the unconfirmed optimistic row itself, so
 * equality also means it is still tagged as a local create and nothing has edited or confirmed it
 * since. A request the server did apply before the cancellation is undone locally until the next
 * sync brings it back, which is acceptable here.
 *
 * The cancellation is always what propagates. A revert that fails itself, for example because the
 * database driver is already closed at shutdown, is attached to it as a suppressed exception.
 *
 * Without this a cancelled create, typically a ViewModel scope ending as the user leaves the
 * screen, would leave its optimistic row behind for good: neither sync removes a local create.
 */
internal suspend inline fun <T> TodosLocalSource.revertingOnCancel(
    id: TodoId,
    expected: TodoEntity?,
    previous: TodoEntity?,
    send: () -> T,
): T =
    try {
        send()
    } catch (cancellation: CancellationException) {
        try {
            withContext(NonCancellable) { replaceIfUnchanged(id, expected, previous) }
        } catch (@Suppress("TooGenericExceptionCaught") revertFailure: Exception) {
            cancellation.addSuppressed(revertFailure)
        }
        throw cancellation
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
            // A local create is owned locally until the list sync reconciles it, exactly as
            // `replaceFromServer` keeps it, so only a server row's 404 confirms an absence.
            if (localSource.find(id)?.localCreated == 0L) localSource.delete(id)
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
