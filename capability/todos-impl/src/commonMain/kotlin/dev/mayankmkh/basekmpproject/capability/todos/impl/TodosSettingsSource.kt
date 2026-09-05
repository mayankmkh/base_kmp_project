package dev.mayankmkh.basekmpproject.capability.todos.impl

import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStore
import dev.mayankmkh.basekmpproject.foundation.preferences.booleanPrefKey
import dev.mayankmkh.basekmpproject.foundation.preferences.stringPrefKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class TodosSettingsSource(private val store: PreferenceStore) {
    fun observe(): Flow<TodoSettings> =
        combine(store.observe(HideCompletedKey), store.observe(SortKey)) { hideCompleted, sort ->
            TodoSettings(
                hideCompleted = hideCompleted ?: false,
                sort =
                    sort?.let { stored -> TodoSort.entries.find { it.name == stored } }
                        ?: TodoSort.ID,
            )
        }

    suspend fun update(settings: TodoSettings) {
        store.edit {
            set(HideCompletedKey, settings.hideCompleted)
            set(SortKey, settings.sort.name)
        }
    }

    private companion object {
        val HideCompletedKey = booleanPrefKey("hide_completed")
        val SortKey = stringPrefKey("sort")
    }
}
