package dev.mayankmkh.basekmpproject.capability.posts.impl

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostFeed
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.SyncCoordinator
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.commit
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.observations
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.reconnects
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class PostsCapabilityImpl(
    private val remoteSource: PostsRemoteSource,
    private val localSource: PostsLocalSource,
    applicationRuntimeScope: ApplicationRuntimeScope,
    connectivityMonitor: ConnectivityMonitor,
    private val logger: Logger,
) : PostsQueries, PostsCommands, AutoCloseable {
    private val scope = applicationRuntimeScope.childScope("posts")

    private val feedSync =
        SyncCoordinator<Unit>(
            scope,
            sync = { _, _ -> syncFeed() },
            retryTriggers = connectivityMonitor.reconnects(),
        )

    private val postSync =
        SyncCoordinator<PostId>(
            scope,
            sync = { id, _ -> syncPost(id) },
            retryTriggers = connectivityMonitor.reconnects(),
        )

    override fun observeFeed(): Flow<ResourceObservation<PostFeed>> =
        feedSync.observations(
            Unit,
            combine(localSource.observeFeed(), localSource.observeFeedInitialized()) {
                posts,
                initialised ->
                if (initialised) PostFeed(posts.map(PostEntity::toPost)) else null
            },
        )

    override fun observePost(id: PostId): Flow<ResourceObservation<Post>> =
        postSync.observations(id, localSource.observeById(id.value.toString()).map { it?.toPost() })

    override suspend fun refreshFeed(qos: RefreshQos): Outcome<Unit> = feedSync.sync(Unit, qos)

    override suspend fun refreshPost(id: PostId, qos: RefreshQos): Outcome<Unit> =
        postSync.sync(id, qos)

    private suspend fun syncFeed(): Outcome<Unit> =
        remoteSource.getPosts().commit(logger, "posts.feed.refresh") { posts ->
            localSource.replaceFeed(posts.map(PostDto::toPostEntity))
        }

    private suspend fun syncPost(id: PostId): Outcome<Unit> =
        remoteSource.getPost(id.value).commit(logger, "posts.detail.refresh") { post ->
            if (post == null) localSource.delete(id.value.toString())
            else localSource.upsert(post.toPostEntity())
        }

    override fun close() {
        scope.cancel()
    }
}

private fun PostDto.toPostEntity() = PostEntity(id.toString(), title, body, authorId = userId)

private fun PostEntity.toPost() = Post(PostId(id.toLong()), authorId, title, body)
