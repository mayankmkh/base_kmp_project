package dev.mayankmkh.basekmpproject.capability.posts.impl

import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostFeed
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import dev.mayankmkh.basekmpproject.storage.database.PostEntity
import dev.mayankmkh.basekmpproject.storage.database.PostsLocalStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mobilenativefoundation.store.store5.Converter
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.FetcherResult
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.Validator

internal class PostsCapabilityImpl(
    postsApi: PostsApi,
    postsLocalStore: PostsLocalStore,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
) : PostsQueries, PostsCommands, AutoCloseable {
    private val scope = applicationRuntimeScope.childScope("posts")

    private val feedStore: Store<Unit, FeedSnapshot> =
        StoreBuilder.from(
                fetcher =
                    Fetcher.ofResult { _: Unit ->
                        postsApi
                            .getPosts()
                            .fold(
                                success = { posts -> FetcherResult.Data(posts) },
                                failure = { apiError -> FetcherResult.Error.Custom(apiError) },
                            )
                    },
                sourceOfTruth =
                    SourceOfTruth.of<Unit, List<PostEntity>, FeedSnapshot>(
                        reader = {
                            combine(
                                postsLocalStore.observeAll(),
                                postsLocalStore.observeFeedInitialized(),
                            ) { posts, initialized ->
                                FeedSnapshot(
                                    feed = PostFeed(posts.map(PostEntity::toPost)),
                                    initialized = initialized,
                                )
                            }
                        },
                        writer = { _, posts -> postsLocalStore.replaceAll(posts) },
                    ),
                converter =
                    Converter.Builder<List<PostDto>, List<PostEntity>, FeedSnapshot>()
                        .fromNetworkToLocal { posts -> posts.map(PostDto::toPostEntity) }
                        .fromOutputToLocal { snapshot ->
                            snapshot.feed.posts.map(Post::toPostEntity)
                        }
                        .build(),
            )
            // A fetched empty feed is authoritative. The durable marker distinguishes it from a
            // database that has never synchronized.
            .validator(Validator.by { snapshot -> snapshot.initialized })
            // SQLDelight is shared by feed and detail resources and remains the only memory of
            // truth; a Store cache could briefly mask a sibling resource write.
            .disableCache()
            .scope(scope)
            .build()

    private val postStore: Store<PostId, Post> =
        StoreBuilder.from(
                fetcher =
                    Fetcher.ofResult { id: PostId ->
                        postsApi
                            .getPost(id.value)
                            .fold(
                                success = { post -> FetcherResult.Data(post) },
                                failure = { apiError -> FetcherResult.Error.Custom(apiError) },
                            )
                    },
                sourceOfTruth =
                    SourceOfTruth.of<PostId, PostEntity, Post>(
                        reader = { id ->
                            postsLocalStore.observeById(id.value.toString()).map { entity ->
                                entity?.toPost()
                            }
                        },
                        writer = { _, post -> postsLocalStore.upsert(post) },
                    ),
                converter =
                    Converter.Builder<PostDto, PostEntity, Post>()
                        .fromNetworkToLocal(PostDto::toPostEntity)
                        .fromOutputToLocal(Post::toPostEntity)
                        .build(),
            )
            .disableCache()
            .scope(scope)
            .build()

    private val feedResource =
        StoreResource(
            scope = scope,
            store = feedStore,
            key = Unit,
            mapValue = FeedSnapshot::feed,
        )

    // Reached from every observer and command coroutine, so the map is only ever touched while
    // holding this mutex: `getOrPut` is not atomic, and a lost race would start a second
    // collector for the same post inside the capability scope.
    private val postResourcesMutex = Mutex()
    private val postResources = mutableMapOf<PostId, StoreResource<PostId, Post, Post>>()

    init {
        // Reconnect is capability/resource policy, not presentation lifetime. Background QoS is
        // recorded at the command boundary even though the current transport executes all QoS
        // classes immediately (there is no shared refresh scheduler in this adoption phase).
        scope.launch {
            connectivityMonitor.reconnects().collect { refreshFeed(RefreshQos.background()) }
        }
    }

    override fun observeFeed(): Flow<ResourceObservation<PostFeed>> = feedResource.observations

    override fun observePost(id: PostId): Flow<ResourceObservation<Post>> = flow {
        emitAll(postResource(id).observations)
    }

    override suspend fun refreshFeed(qos: RefreshQos) {
        feedResource.refresh(qos)
    }

    override suspend fun refreshPost(id: PostId, qos: RefreshQos) {
        postResource(id).refresh(qos)
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun postResource(id: PostId): StoreResource<PostId, Post, Post> =
        postResourcesMutex.withLock {
            postResources.getOrPut(id) {
                StoreResource(
                    scope = scope,
                    store = postStore,
                    key = id,
                    mapValue = { post -> post },
                )
            }
        }
}

internal data class FeedSnapshot(val feed: PostFeed, val initialized: Boolean)

private fun PostDto.toPostEntity() = PostEntity(id.toString(), title, body, authorId = userId)

private fun PostEntity.toPost() = Post(PostId(id.toLong()), authorId, title, body)

private fun Post.toPostEntity() = PostEntity(id.value.toString(), title, body, authorId = authorId)
