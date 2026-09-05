package dev.mayankmkh.basekmpproject.feature.todos

import dev.mayankmkh.basekmpproject.capability.todos.api.Todo
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.resource.Violation

internal object TodosFeatureFixtures {
    val todos =
        listOf(
            Todo(TodoId(1), 1, "Buy groceries", completed = false),
            Todo(TodoId(2), 1, "Write release notes", completed = true),
        )
    val loadingList = TodoListState()
    val emptyList = TodoListState(isInitialLoading = false)
    val list = TodoListState(todos = todos, isInitialLoading = false)
    val errorList: TodoListState =
        TodoListState(
            isInitialLoading = false,
            problem = Problem(ProblemKind.SERVER),
        )
    val offlineList: TodoListState =
        TodoListState(
            todos = todos,
            isInitialLoading = false,
            problem = Problem(ProblemKind.OFFLINE),
        )
    val detail: TodoDetailState =
        TodoDetailState(
            todo = todos.first(),
            title = todos.first().title,
            isInitialLoading = false,
        )
    val detailNotFound: TodoDetailState =
        TodoDetailState(
            isInitialLoading = false,
            isAbsent = true,
        )
    val editor = TodoEditorState()
    val editorFieldError: TodoEditorState =
        TodoEditorState(violations = listOf(Violation(TodoField.TITLE, "blank")))
    val editorServerMessage: TodoEditorState =
        TodoEditorState(
            title = "A server-rejected title",
            violations =
                listOf(
                    Violation(
                        field = TodoField.TITLE,
                        code = "too_long",
                        message = "Use the shorter title supplied by your team.",
                    )
                ),
        )
    val summary = TodoSummaryState(open = 1, completed = 1, isLoading = false)
}
