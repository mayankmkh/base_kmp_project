@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.mayankmkh.basekmpproject.app.shared.nav.AppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoEditorRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoListRoute
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailScreen
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorScreen
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListScreen
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey

private const val TodosSceneKey = "todos-list-detail"

internal fun EntryProviderScope<NavKey>.todosEntries(navigationState: AppNavigationState) {
    entry<TodoListRoute>(metadata = ListDetailSceneStrategy.listPane(sceneKey = TodosSceneKey)) {
        TodoListEntry(navigationState)
    }
    entry<TodoDetailRoute>(
        metadata = ListDetailSceneStrategy.detailPane(sceneKey = TodosSceneKey)
    ) { route ->
        TodoDetailEntry(route, navigationState)
    }
    entry<TodoEditorRoute> { TodoEditorEntry(navigationState) }
}

@Composable
private fun TodoListEntry(navigationState: AppNavigationState) {
    TodoListScreen(
        instanceKey = FeatureInstanceKey.forScreen("todos/list", "todo-list"),
        onOutput = { output ->
            when (output) {
                is TodoListOutput.OpenDetail ->
                    navigationState.navigate(TodoDetailRoute(output.id.value))
                TodoListOutput.OpenEditor -> navigationState.navigate(TodoEditorRoute)
            }
        },
    )
}

@Composable
private fun TodoDetailEntry(route: TodoDetailRoute, navigationState: AppNavigationState) {
    TodoDetailScreen(
        todoId = TodoId(route.id),
        instanceKey = FeatureInstanceKey.forScreen("todos/detail/${route.id}", "todo-detail"),
        onOutput = { output ->
            when (output) {
                TodoDetailOutput.Back,
                is TodoDetailOutput.Deleted,
                is TodoDetailOutput.NotFound -> navigationState.goBack()
            }
        },
    )
}

@Composable
private fun TodoEditorEntry(navigationState: AppNavigationState) {
    TodoEditorScreen(
        instanceKey = FeatureInstanceKey.forScreen("todos/editor", "todo-editor"),
        onOutput = { output ->
            when (output) {
                TodoEditorOutput.Back -> navigationState.goBack()
                is TodoEditorOutput.Created ->
                    navigationState.navigate(TodoDetailRoute(output.id.value), replaceTop = true)
            }
        },
    )
}
