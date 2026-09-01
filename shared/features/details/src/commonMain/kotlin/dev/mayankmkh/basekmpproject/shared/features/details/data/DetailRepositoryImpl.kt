package dev.mayankmkh.basekmpproject.shared.features.details.data

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.shared.libs.database.PostEntity
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.getOrThrow
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostDto
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DetailRepositoryImpl(
    private val postsApi: PostsApi,
    private val postsLocalStore: PostsLocalStore,
    private val appDispatchers: AppDispatchers,
    private val failureListener: NetworkBoundResource.OnFailureListener,
) : DetailRepository {

    override fun getItem(id: String): Flow<Result<Item, Throwable>> =
        object : NetworkBoundResource<PostDto, Item>(appDispatchers, failureListener) {
                override fun loadFromDb() =
                    postsLocalStore.observeById(id).map { post -> post?.toItem() }

                // The single-item fetch rule: nothing cached under this id. On a warm cache this
                // never hits the network; on a cold deep link it always does.
                override suspend fun shouldFetch(data: Item?) = data == null

                override suspend fun loadFromNetwork() = postsApi.getPost(id).getOrThrow()

                override suspend fun processResponse(response: PostDto) = response.toItem()

                // `upsert`, not `replaceAll`: this call knows about one post and must not evict
                // the feed the list screen is showing.
                override suspend fun saveCallResult(item: Item) {
                    postsLocalStore.upsert(item.toPostEntity())
                }
            }
            .asFlow()

    private fun PostDto.toItem() = Item(id = id.toString(), title = title, text = body)

    private fun PostEntity.toItem() = Item(id = id, title = title, text = body)

    private fun Item.toPostEntity() = PostEntity(id = id, title = title, body = text)
}
