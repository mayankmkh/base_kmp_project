package dev.mayankmkh.basekmpproject.capability.todos.impl

import dev.mayankmkh.basekmpproject.capability.todos.impl.db.AppDatabase
import dev.mayankmkh.basekmpproject.testkit.asProvider
import dev.mayankmkh.basekmpproject.testkit.inMemorySqliteDriver

internal fun createInMemoryTodosLocalSource(): TodosLocalSource {
    val driver = inMemorySqliteDriver(AppDatabase.Schema)
    return TodosLocalSource(driver.asProvider())
}
