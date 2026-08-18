package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Internal rather than private so tests can hand it a store that needs no file behind it.
class KeyValueStore
internal constructor(private val dataStore: DataStore<Preferences>, private val json: Json) {

    constructor(
        prefContext: PrefContext,
        json: Json,
    ) : this(createDataStore(prefContext, PrefFile.KEY_VALUE), json)

    suspend fun getItems(): List<ItemEntity>? =
        dataStore.getStringOrNull(Keys.ITEMS)?.let {
            json.decodeFromString(ListSerializer(ItemEntity.serializer()), it)
        }

    fun getItemsFlow(): Flow<List<ItemEntity>?> =
        dataStore.getStringOrNullFlow(Keys.ITEMS).map { value ->
            value?.let { json.decodeFromString(ListSerializer(ItemEntity.serializer()), it) }
        }

    suspend fun saveItems(itemEntities: List<ItemEntity>) =
        dataStore.putString(
            Keys.ITEMS,
            json.encodeToString(ListSerializer(ItemEntity.serializer()), itemEntities),
        )

    private enum class Keys(override val key: String) : PrefKey {
        ITEMS("items")
    }
}

@Serializable data class ItemEntity(val id: String, val title: String, val text: String)
