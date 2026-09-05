package dev.mayankmkh.basekmpproject.capability.todos.api

import kotlin.jvm.JvmInline

@JvmInline public value class TodoId(public val value: Long)

public data class Todo(
    val id: TodoId,
    val ownerId: Long,
    val title: String,
    val completed: Boolean,
)

public data class TodoList(val todos: List<Todo>)

public data class TodoDraft(val title: String, val ownerId: Long)

public data class TodoSettings(
    val hideCompleted: Boolean = false,
    val sort: TodoSort = TodoSort.ID,
)

public enum class TodoSort {
    ID,
    TITLE,
}

public enum class TodoField {
    TITLE,
    OWNER_ID,
}

public data class TodoViolation(
    val field: TodoField?,
    val code: String,
    val message: String?,
)
