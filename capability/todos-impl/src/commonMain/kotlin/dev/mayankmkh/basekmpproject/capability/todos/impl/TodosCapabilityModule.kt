package dev.mayankmkh.basekmpproject.capability.todos.impl

import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.foundation.preferences.PrefFile
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

public val todosCapabilityModule: Module = module {
    singleOf(::TodosLocalSource)
    single { TodosSettingsSource(get<PreferenceStores>().open(PrefFile("todos.settings"))) }
    single { TodosRemoteSource(get(), get()) }
    single { TodosCapabilityImpl(get(), get(), get(), get(), get(), get()) } onClose { it?.close() }
    single<TodosQueries> { get<TodosCapabilityImpl>() }
    single<TodosCommands> { get<TodosCapabilityImpl>() }
}
