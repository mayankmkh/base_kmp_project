@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.mayankmkh.basekmpproject.app.shared.nav.AppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoEditorRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoListRoute
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailCellType
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoDetailScreen
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorCellType
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoEditorScreen
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListCellType
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListOutput
import dev.mayankmkh.basekmpproject.feature.todos.api.TodoListScreen
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey

private const val TodosSceneKey = "todos-list-detail"

private val TodoListInstanceKey =
    FeatureInstanceKey.forScreen(TodoListRoute.instanceSurface, TodoListCellType)

private val TodoEditorInstanceKey =
    FeatureInstanceKey.forScreen(TodoEditorRoute.instanceSurface, TodoEditorCellType)

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
        instanceKey = TodoListInstanceKey,
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
        instanceKey =
            remember(route) {
                FeatureInstanceKey.forScreen(route.instanceSurface, TodoDetailCellType)
            },
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
        instanceKey = TodoEditorInstanceKey,
        onOutput = { output ->
            when (output) {
                TodoEditorOutput.Back -> navigationState.goBack()
                is TodoEditorOutput.Created ->
                    navigationState.navigate(TodoDetailRoute(output.id.value), replaceTop = true)
            }
        },
    )
}
