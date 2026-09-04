package dev.mayankmkh.basekmpproject.capability.posts.impl

import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostFeed
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.store5.StoreResource
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mobilenativefoundation.store.store5.Converter
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.FetcherResult
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.Validator

internal class PostsCapabilityImpl(
    remoteSource: PostsRemoteSource,
    localSource: PostsLocalSource,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
) : PostsQueries, PostsCommands, AutoCloseable {
    private val scope = applicationRuntimeScope.childScope("posts")

    private val feedStore: Store<Unit, FeedSnapshot> =
        StoreBuilder.from(
                fetcher =
                    Fetcher.ofResult { _: Unit ->
                        remoteSource
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
                                localSource.observeAll(),
                                localSource.observeFeedInitialized(),
                            ) { posts, initialized ->
                                FeedSnapshot(
                                    feed = PostFeed(posts.map(PostEntity::toPost)),
                                    initialized = initialized,
                                )
                            }
                        },
                        writer = { _, posts -> localSource.replaceAll(posts) },
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
                        remoteSource
                            .getPost(id.value)
                            .fold(
                                success = { post -> FetcherResult.Data(post) },
                                failure = { apiError -> FetcherResult.Error.Custom(apiError) },
                            )
                    },
                sourceOfTruth =
                    SourceOfTruth.of<PostId, PostEntity, Post>(
                        reader = { id ->
                            localSource.observeById(id.value.toString()).map { entity ->
                                entity?.toPost()
                            }
                        },
                        writer = { _, post -> localSource.upsert(post) },
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

    private val postResourcesMutex = Mutex()
    private val postResources = mutableMapOf<PostId, PostResourceLease>()

    // A reconnect that arrives while nobody observes the feed is remembered rather than acted on,
    // so no request is spent on data nobody is looking at. The first observer to arrive afterwards
    // settles the debt, which is the "first subscriber ensures current state" step of the Helix
    // subscriber model. Both writers run in the capability scope.
    private val feedRevalidationPending = MutableStateFlow(false)

    init {
        // Reconnect is capability policy, not presentation lifetime. Background QoS is recorded at
        // the command boundary even though the current transport executes every class immediately.
        scope.launch {
            connectivityMonitor.reconnects().collect {
                if (feedResource.subscriptionCount.value > 0) {
                    refreshFeed(RefreshQos.background())
                } else {
                    feedRevalidationPending.value = true
                }
            }
        }
        scope.launch {
            feedResource.subscriptionCount
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .collect { observed ->
                    if (
                        observed &&
                            feedRevalidationPending.compareAndSet(expect = true, update = false)
                    ) {
                        refreshFeed(RefreshQos.background())
                    }
                }
        }
    }

    override fun observeFeed(): Flow<ResourceObservation<PostFeed>> = feedResource.observations

    override fun observePost(id: PostId): Flow<ResourceObservation<Post>> = flow {
        withPostResource(id) { emitAll(it.observations) }
    }

    // Any successful feed refresh, including one driven by a background job, settles the deferred
    // reconnect debt so the next first observer does not repeat it.
    override suspend fun refreshFeed(qos: RefreshQos): RefreshOutcome =
        feedResource.refresh(qos).also { outcome ->
            if (outcome is RefreshOutcome.Succeeded) feedRevalidationPending.value = false
        }

    override suspend fun refreshPost(id: PostId, qos: RefreshQos): RefreshOutcome =
        withPostResource(id) { it.refresh(qos) }

    override fun close() {
        scope.cancel()
    }

    // Lease changes and zero-count eviction share one critical section. A new caller either joins
    // the existing positive lease count or creates a resource after the old one is removed.
    private suspend fun <T> withPostResource(
        id: PostId,
        block: suspend (StoreResource<PostId, Post, Post>) -> T,
    ): T {
        val lease = postResourcesMutex.withLock {
            postResources
                .getOrPut(id) {
                    PostResourceLease(
                        StoreResource(
                            scope = scope,
                            store = postStore,
                            key = id,
                            mapValue = { post -> post },
                        )
                    )
                }
                .also { it.count++ }
        }
        try {
            return block(lease.resource)
        } finally {
            // The release must run even when the caller was cancelled, or the lease would leak and
            // the resource would stay hot forever. Taking the mutex suspends, so make it immune.
            withContext(NonCancellable) {
                postResourcesMutex.withLock {
                    lease.count--
                    if (lease.count == 0) {
                        postResources.remove(id)
                        lease.resource.close()
                    }
                }
            }
        }
    }

    // Test seam for verifying that every map entry has a live lease.
    internal suspend fun postResourceCountForTest(): Int = postResourcesMutex.withLock {
        postResources.size
    }
}

private class PostResourceLease(
    val resource: StoreResource<PostId, Post, Post>,
    var count: Int = 0,
)

internal data class FeedSnapshot(val feed: PostFeed, val initialized: Boolean)

private fun PostDto.toPostEntity() = PostEntity(id.toString(), title, body, authorId = userId)

private fun PostEntity.toPost() = Post(PostId(id.toLong()), authorId, title, body)

private fun Post.toPostEntity() = PostEntity(id.value.toString(), title, body, authorId = authorId)
