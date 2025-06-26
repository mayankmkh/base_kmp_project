package dev.mayankmkh.basekmpproject.shared.features.details.data

import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.libs.prefs.ItemEntity
import dev.mayankmkh.basekmpproject.shared.libs.prefs.KeyValueStore

internal class DetailRepositoryImpl(private val keyValueStore: KeyValueStore) : DetailRepository {
    override suspend fun getItem(id: String): Item {
        val itemEntity = keyValueStore.getItems()?.single { it.id == id }
        checkNotNull(itemEntity)
        return itemEntity.toItem()
    }

    private fun ItemEntity.toItem() = Item(id, title, text)
}
