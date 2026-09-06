package dev.mayankmkh.basekmpproject.feature.todos.api

import dev.mayankmkh.basekmpproject.feature.todos.TodoDetailViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodoEditorViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodoListViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodoSummaryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

public val todosFeatureModule: Module = module {
    viewModel<TodoListViewModel>()
    viewModel { parameters ->
        TodoDetailViewModel(
            todoId = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
    viewModel<TodoEditorViewModel>()
    viewModel<TodoSummaryViewModel>()
}
