package dev.mayankmkh.basekmpproject.shared.features.details.data

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.streamAsResult
import dev.mayankmkh.basekmpproject.shared.libs.database.PostEntity
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.getOrThrow
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostDto
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Converter
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

internal class DetailRepositoryImpl(
    postsApi: PostsApi,
    postsLocalStore: PostsLocalStore,
    storeScope: CoroutineScope,
) : DetailRepository {

    private val store: Store<String, Item> =
        StoreBuilder.from(
                fetcher = Fetcher.of { id: String -> postsApi.getPost(id).getOrThrow() },
                sourceOfTruth =
                    SourceOfTruth.of<String, PostEntity, Item>(
                        reader = { id ->
                            postsLocalStore.observeById(id).map { post -> post?.toItem() }
                        },
                        // Upsert one row: opening a cold deep link must not evict the feed behind
                        // the details screen.
                        writer = { _, post -> postsLocalStore.upsert(post) },
                    ),
                converter =
                    Converter.Builder<PostDto, PostEntity, Item>()
                        .fromNetworkToLocal { post -> post.toPostEntity() }
                        .fromOutputToLocal { item -> item.toPostEntity() }
                        .build(),
            )
            // The list store writes the same table, so SQLDelight must remain the only cache.
            .disableCache()
            .scope(storeScope)
            .build()

    override fun getItem(id: String): Flow<Result<Item, Throwable>> =
        store.streamAsResult(StoreReadRequest.cached(id, refresh = false))

    private fun PostEntity.toItem() = Item(id = id, title = title, text = body)

    private fun PostDto.toPostEntity() = PostEntity(id = id.toString(), title = title, body = body)

    private fun Item.toPostEntity() = PostEntity(id = id, title = title, body = text)
}
