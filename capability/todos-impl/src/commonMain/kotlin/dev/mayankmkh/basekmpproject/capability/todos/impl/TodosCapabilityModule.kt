package dev.mayankmkh.basekmpproject.capability.todos.impl

import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.plugin.module.dsl.single

public val todosCapabilityModule: Module = module {
    single<TodosLocalSource>()
    single<TodosSettingsSource>()
    single<TodosRemoteSource>()
    single<TodosCapabilityImpl>() onClose { it?.close() }
    // Aliases expose contracts only; onClose belongs to the implementation definition above.
    single<TodosQueries> { get<TodosCapabilityImpl>() }
    single<TodosCommands> { get<TodosCapabilityImpl>() }
}
