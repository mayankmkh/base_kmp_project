package dev.mayankmkh.basekmpproject.capability.posts.api

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import kotlinx.coroutines.flow.Flow

public interface PostsQueries {
    public fun observeFeed(): Flow<ResourceObservation<PostFeed>>

    public fun observePost(id: PostId): Flow<ResourceObservation<Post>>
}

/**
 * Explicit synchronization intents. Commands complete when the attempt completes but deliberately
 * return no payload: observers are the source of truth and carry failures without clearing cached
 * values.
 */
public interface PostsCommands {
    public suspend fun refreshFeed(qos: RefreshQos = RefreshQos.visible())

    public suspend fun refreshPost(id: PostId, qos: RefreshQos = RefreshQos.visible())
}
