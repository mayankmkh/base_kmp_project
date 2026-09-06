package dev.mayankmkh.basekmpproject.capability.todos.impl

import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.foundation.preferences.PrefFile
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * The Todos settings file.
 *
 * Settings are one value with a fixed set of fields, not independent flags, so they live in one
 * document (preferences.md section 7): an update writes the whole value in one edit and a reader
 * never observes half of it.
 */
internal class TodosSettingsSource(stores: PreferenceStores) {
    private val store =
        stores.openDocument(
            PrefFile("todos.settings"),
            TodoSettingsDocument.serializer(),
            TodoSettingsDocument(),
        )

    fun observe(): Flow<TodoSettings> =
        store.data.map(TodoSettingsDocument::toSettings).distinctUntilChanged()

    suspend fun update(settings: TodoSettings) {
        store.update { settings.toDocument() }
    }
}

/**
 * The stored shape of [TodoSettings]. Every field has a default, so a file written by a build that
 * knew fewer fields still decodes, and an unknown [TodoSort] constant reads back as the default
 * under the module's lenient JSON rather than losing the file.
 */
@Serializable
private data class TodoSettingsDocument(
    val hideCompleted: Boolean = false,
    val sort: TodoSort = TodoSort.ID,
)

private fun TodoSettingsDocument.toSettings() = TodoSettings(hideCompleted, sort)

private fun TodoSettings.toDocument() = TodoSettingsDocument(hideCompleted, sort)
