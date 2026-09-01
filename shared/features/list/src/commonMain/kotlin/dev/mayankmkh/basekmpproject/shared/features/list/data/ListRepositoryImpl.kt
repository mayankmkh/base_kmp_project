package dev.mayankmkh.basekmpproject.shared.features.list.data

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.streamAsResult
import dev.mayankmkh.basekmpproject.shared.libs.database.PostEntity
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.getOrThrow
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostDto
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.Validator
import org.mobilenativefoundation.store.store5.impl.extensions.fresh

internal class ListRepositoryImpl(
    postsApi: PostsApi,
    postsLocalStore: PostsLocalStore,
    storeScope: CoroutineScope,
) : ListRepository {

    private val store: Store<Unit, Collection<Item>> =
        StoreBuilder.from(
                fetcher = Fetcher.of { postsApi.getPosts().getOrThrow() },
                sourceOfTruth =
                    SourceOfTruth.of<Unit, List<PostDto>, Collection<Item>>(
                        reader = {
                            postsLocalStore.observeAll().map { posts ->
                                posts.map { it.toItem() }
                            }
                        },
                        writer = { _, posts ->
                            postsLocalStore.replaceAll(posts.map { it.toPostEntity() })
                        },
                    ),
            )
            // An empty database means Store should fetch. Values just fetched are not validated,
            // so an authoritative empty response still reaches collectors as an empty feed.
            .validator(Validator.by { items -> items.isNotEmpty() })
            // SQLDelight is shared by the list and details stores. Keeping another cache here
            // could briefly serve a stale value after the other store writes the database.
            .disableCache()
            // Store otherwise falls back to GlobalScope, which would outlive this repository's
            // application/test owner.
            .scope(storeScope)
            .build()

    override fun getItems(): Flow<Result<Collection<Item>, Throwable>> =
        store.streamAsResult(StoreReadRequest.cached(Unit, refresh = false))

    override suspend fun refresh() {
        // Store's fresh request always calls the fetcher and performs the source-of-truth write.
        // The open read observes that same write, so the returned value is intentionally ignored.
        store.fresh(Unit)
    }

    private fun PostDto.toPostEntity() = PostEntity(id = id.toString(), title = title, body = body)

    private fun PostEntity.toItem() = Item(id = id, title = title, text = body)
}
