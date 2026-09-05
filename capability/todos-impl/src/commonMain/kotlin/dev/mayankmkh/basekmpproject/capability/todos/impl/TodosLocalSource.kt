package dev.mayankmkh.basekmpproject.capability.todos.impl

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.capability.todos.impl.db.AppDatabase
import dev.mayankmkh.basekmpproject.foundation.sqldelight.LazyDatabase
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observe
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeList
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeOne
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Durable Todos truth and the JSONPlaceholder demo merge rule.
 *
 * A full refresh replaces every server-backed row and leaves rows tagged as locally created
 * untouched. A server row therefore overwrites optimistic edits to its local copy, while a todo
 * created in this app survives refresh even though JSONPlaceholder never persists writes. Command
 * responses reconcile into the same row, but a create response's constant id 201 is ignored in
 * favor of the locally allocated id. A real backend with persistent writes removes the tag and this
 * merge exception.
 */
internal class TodosLocalSource(drivers: SqlDriverProvider) {
    private val database = LazyDatabase(drivers, AppDatabase::invoke)

    fun observeTodos(settings: TodoSettings): Flow<List<TodoEntity>> = database.observe {
        todosSchemaQueries
            .selectTodos(
                hideCompleted = settings.hideCompleted.toLong(),
                byTitle = (settings.sort == TodoSort.TITLE).toLong(),
                ::TodoEntity,
            )
            .observeList()
    }

    fun observeById(id: TodoId): Flow<TodoEntity?> = database.observe {
        todosSchemaQueries.selectById(id.value, ::TodoEntity).observeOneOrNull()
    }

    fun observeInitialized(): Flow<Boolean> = database.observe {
        todosSchemaQueries.initializationCount().observeOne().map { it > 0L }.distinctUntilChanged()
    }

    suspend fun find(id: TodoId): TodoEntity? =
        database.get().todosSchemaQueries.selectById(id.value, ::TodoEntity).awaitAsOneOrNull()

    suspend fun allocateLocalId(): TodoId =
        TodoId(database.get().todosSchemaQueries.nextLocalId().awaitAsOne().max ?: LocalIdStart)

    suspend fun replaceFromServer(todos: List<TodoEntity>) {
        val database = database.get()
        database.transaction {
            database.todosSchemaQueries.deleteServerBackedRows()
            todos.distinctBy { it.id }.forEach { database.todosSchemaQueries.upsert(it) }
            database.todosSchemaQueries.markInitialized()
        }
    }

    suspend fun upsert(todo: TodoEntity) {
        database.get().todosSchemaQueries.upsert(todo)
    }

    suspend fun delete(id: TodoId) {
        database.get().todosSchemaQueries.deleteById(id.value)
    }
}

private suspend fun dev.mayankmkh.basekmpproject.capability.todos.impl.db.TodosSchemaQueries.upsert(
    todo: TodoEntity
) {
    upsert(
        id = todo.id,
        owner_id = todo.ownerId,
        title = todo.title,
        completed = todo.completed,
        local_created = todo.localCreated,
    )
}

internal data class TodoEntity(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val completed: Long,
    val localCreated: Long,
)

internal fun Boolean.toLong(): Long = if (this) 1 else 0

private const val LocalIdStart = 1_000_000L
