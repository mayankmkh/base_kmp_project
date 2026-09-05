package dev.mayankmkh.basekmpproject.feature.todos.api

import dev.mayankmkh.basekmpproject.feature.todos.TodoDetailViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodoEditorViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodoListViewModel
import dev.mayankmkh.basekmpproject.feature.todos.TodoSummaryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val todosFeatureModule: Module = module {
    viewModel { parameters ->
        TodoListViewModel(
            instanceKey = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
    viewModel { parameters ->
        TodoDetailViewModel(
            todoId = parameters.get(),
            instanceKey = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
    viewModel { parameters ->
        TodoEditorViewModel(
            instanceKey = parameters.get(),
            commands = get(),
        )
    }
    viewModel { parameters ->
        TodoSummaryViewModel(
            instanceKey = parameters.get(),
            queries = get(),
        )
    }
}
