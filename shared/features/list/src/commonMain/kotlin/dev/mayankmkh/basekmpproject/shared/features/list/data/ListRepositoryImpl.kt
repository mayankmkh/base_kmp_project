package dev.mayankmkh.basekmpproject.shared.features.list.data

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundCollectionResource
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.shared.libs.prefs.ItemEntity
import dev.mayankmkh.basekmpproject.shared.libs.prefs.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ListRepositoryImpl(
    private val keyValueStore: KeyValueStore,
    private val appDispatchers: AppDispatchers,
    private val failureListener: NetworkBoundResource.OnFailureListener,
) : ListRepository {
    override fun getItems(): Flow<Result<Collection<Item>, Throwable>> {
        return object :
                NetworkBoundCollectionResource<List<ItemDto>, Item>(
                    appDispatchers,
                    failureListener,
                ) {
                override suspend fun processResponse(response: List<ItemDto>) =
                    response.map { it.toItem() }

                override suspend fun saveCallResult(item: Collection<Item>) {
                    keyValueStore.saveItems(item.map { it.toItemEntity() })
                }

                override suspend fun shouldFetch(data: Collection<Item>?) = data.isNullOrEmpty()

                override fun loadFromDb() =
                    keyValueStore.getItemsFlow().map { list -> list?.map { it.toItem() } }

                override suspend fun loadFromNetwork() =
                    List(ITEMS_COUNT) { index ->
                        ItemDto(
                            id = index.toString(),
                            title = "Item $index",
                            text = "Item $index. ".repeat(ITEM_REPEAT_COUNT),
                        )
                    }
            }
            .asFlow()
    }

    private data class ItemDto(val id: String, val title: String, val text: String)

    private fun ItemDto.toItem() = Item(id, title, text)

    private fun Item.toItemEntity() = ItemEntity(id, title, text)

    private fun ItemEntity.toItem() = Item(id, title, text)

    companion object {
        private const val ITEMS_COUNT = 100
        private const val ITEM_REPEAT_COUNT = 1000
    }
}
