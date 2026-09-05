package dev.mayankmkh.basekmpproject.feature.todos.api

import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId

public sealed interface TodoListOutput {
    public data class OpenDetail(val id: TodoId) : TodoListOutput

    public data object OpenEditor : TodoListOutput
}

public sealed interface TodoDetailOutput {
    public data object Back : TodoDetailOutput

    public data class NotFound(val id: TodoId) : TodoDetailOutput

    public data class Deleted(val id: TodoId) : TodoDetailOutput
}

public sealed interface TodoEditorOutput {
    public data object Back : TodoEditorOutput

    public data class Created(val id: TodoId) : TodoEditorOutput
}

public sealed interface TodoSummaryOutput {
    public data object OpenTodos : TodoSummaryOutput
}
