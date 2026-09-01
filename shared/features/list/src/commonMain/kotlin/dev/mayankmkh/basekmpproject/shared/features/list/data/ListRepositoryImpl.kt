package dev.mayankmkh.basekmpproject.shared.features.list.data

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundCollectionResource
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.shared.libs.database.PostEntity
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.getOrThrow
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostDto
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ListRepositoryImpl(
    private val postsApi: PostsApi,
    private val postsLocalStore: PostsLocalStore,
    private val appDispatchers: AppDispatchers,
    private val failureListener: NetworkBoundResource.OnFailureListener,
) : ListRepository {

    override fun getItems(): Flow<Result<Collection<Item>, Throwable>> =
        object :
                NetworkBoundCollectionResource<List<PostDto>, Item>(
                    appDispatchers,
                    failureListener,
                ) {
                override fun loadFromDb() =
                    postsLocalStore.observeAll().map { posts -> posts.map { it.toItem() } }

                override suspend fun loadFromNetwork() = postsApi.getPosts().getOrThrow()

                override suspend fun processResponse(response: List<PostDto>) = response.map {
                    it.toItem()
                }

                override suspend fun saveCallResult(item: Collection<Item>) {
                    postsLocalStore.replaceAll(item.map { it.toPostEntity() })
                }
            }
            .asFlow()

    override suspend fun refresh() {
        val posts = postsApi.getPosts().getOrThrow()
        // Writing the cache is the whole job: `getItems` is observing the same table, so its
        // collector sees the new feed without this returning anything.
        postsLocalStore.replaceAll(posts.map { it.toPostEntity() })
    }

    private fun PostDto.toItem() = Item(id = id.toString(), title = title, text = body)

    // Straight to the cache format. Going via `toItem` would put the *display* mapping on the
    // write path, so trimming a title for the UI would silently change what gets persisted.
    private fun PostDto.toPostEntity() = PostEntity(id = id.toString(), title = title, body = body)

    private fun PostEntity.toItem() = Item(id = id, title = title, text = body)

    private fun Item.toPostEntity() = PostEntity(id = id, title = title, body = text)
}
