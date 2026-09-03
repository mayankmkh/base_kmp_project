package dev.mayankmkh.basekmpproject.capability.posts.api

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import kotlinx.coroutines.flow.Flow

public interface PostsQueries {
    public fun observeFeed(): Flow<ResourceObservation<PostFeed>>

    public fun observePost(id: PostId): Flow<ResourceObservation<Post>>
}

/**
 * Explicit synchronization intents. Commands return the outcome of the attempt so the caller can
 * give transient feedback. Observers remain the source of truth for persistent state, and failures
 * never clear cached values.
 */
public interface PostsCommands {
    public suspend fun refreshFeed(qos: RefreshQos = RefreshQos.visible()): RefreshOutcome

    public suspend fun refreshPost(
        id: PostId,
        qos: RefreshQos = RefreshQos.visible(),
    ): RefreshOutcome
}
