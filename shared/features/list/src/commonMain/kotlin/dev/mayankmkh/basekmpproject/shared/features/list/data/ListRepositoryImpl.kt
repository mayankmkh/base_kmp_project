package dev.mayankmkh.basekmpproject.shared.features.list.data

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.streamAsResult
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.mapResultOk
import dev.mayankmkh.basekmpproject.shared.libs.database.PostEntity
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.getOrThrow
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostDto
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.mobilenativefoundation.store.store5.Converter
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

    private val store: Store<Unit, FeedSnapshot> =
        StoreBuilder.from(
                fetcher = Fetcher.of { postsApi.getPosts().getOrThrow() },
                sourceOfTruth =
                    SourceOfTruth.of<Unit, List<PostEntity>, FeedSnapshot>(
                        reader = {
                            combine(
                                postsLocalStore.observeAll(),
                                postsLocalStore.observeFeedInitialized(),
                            ) { posts, initialized ->
                                FeedSnapshot(
                                    items = posts.map { it.toItem() },
                                    initialized = initialized,
                                )
                            }
                        },
                        writer = { _, posts ->
                            postsLocalStore.replaceAll(posts)
                        },
                    ),
                converter =
                    Converter.Builder<List<PostDto>, List<PostEntity>, FeedSnapshot>()
                        .fromNetworkToLocal { posts -> posts.map { it.toPostEntity() } }
                        .fromOutputToLocal { snapshot ->
                            snapshot.items.map { it.toPostEntity() }
                        }
                        .build(),
            )
            // Validity reflects whether the feed endpoint completed, not whether it returned rows.
            // That makes an authoritative empty feed a valid cached result on later subscriptions.
            .validator(Validator.by { snapshot -> snapshot.initialized })
            // SQLDelight is shared by the list and details stores. Keeping another cache here
            // could briefly serve a stale value after the other store writes the database.
            .disableCache()
            // Store otherwise falls back to GlobalScope, which would outlive this repository's
            // application/test owner.
            .scope(storeScope)
            .build()

    override fun getItems(): Flow<Result<Collection<Item>, Throwable>> =
        store.streamAsResult(StoreReadRequest.cached(Unit, refresh = false)).mapResultOk {
            it.items
        }

    override suspend fun refresh() {
        // Store's fresh request always calls the fetcher and performs the source-of-truth write.
        // The open read observes that same write, so the returned value is intentionally ignored.
        store.fresh(Unit)
    }

    private fun PostDto.toPostEntity() = PostEntity(id = id.toString(), title = title, body = body)

    private fun PostEntity.toItem() = Item(id = id, title = title, text = body)

    private fun Item.toPostEntity() = PostEntity(id = id, title = title, body = text)

    private data class FeedSnapshot(
        val items: Collection<Item>,
        val initialized: Boolean,
    )
}
