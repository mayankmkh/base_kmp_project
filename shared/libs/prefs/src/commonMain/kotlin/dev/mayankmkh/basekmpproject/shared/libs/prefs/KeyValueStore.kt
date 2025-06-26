package dev.mayankmkh.basekmpproject.shared.libs.prefs

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
class KeyValueStore
private constructor(private val settings: FlowSettings, private val json: Json) {

    constructor(
        prefContext: PrefContext,
        json: Json,
    ) : this(createDataStoreSettings(prefContext, PrefFile.KEY_VALUE), json)

    suspend fun getItems(): List<ItemEntity>? =
        settings.getStringOrNull(Keys.ITEMS.key)?.let {
            json.decodeFromString(ListSerializer(ItemEntity.serializer()), it)
        }

    fun getItemsFlow(): Flow<List<ItemEntity>?> =
        settings.getStringOrNullFlow(Keys.ITEMS.key).map { value ->
            value?.let { json.decodeFromString(ListSerializer(ItemEntity.serializer()), it) }
        }

    suspend fun saveItems(itemEntities: List<ItemEntity>) =
        settings.putString(
            Keys.ITEMS.key,
            json.encodeToString(ListSerializer(ItemEntity.serializer()), itemEntities),
        )

    private enum class Keys(override val key: String) : PrefKey {
        ITEMS("items")
    }
}

@Serializable data class ItemEntity(val id: String, val title: String, val text: String)
