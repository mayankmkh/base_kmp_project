package dev.mayankmkh.basekmpproject.testkit

import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostFeed
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

object PostsFixtures {
    fun post(
        id: Long = 1,
        authorId: Long = 10,
        title: String = "Post $id",
        body: String = "Body $id",
    ): Post = Post(PostId(id), authorId, title, body)

    fun feed(posts: List<Post> = listOf(post(1), post(2))): PostFeed = PostFeed(posts)
}

object ResourceObservationFixtures {
    fun <T : Any> idle(value: T): ResourceObservation<T> =
        ResourceObservation(value, ResourceOperation.Idle)

    /** Nothing has synchronized in this process yet; [value] is whatever the database held. */
    fun <T : Any> unsynchronized(value: T? = null): ResourceObservation<T> =
        ResourceObservation(value, ResourceOperation.Unsynchronized)

    fun <T : Any> failed(
        value: T? = null,
        kind: ProblemKind = ProblemKind.UNEXPECTED,
    ): ResourceObservation<T> =
        ResourceObservation(
            value = value,
            operation = ResourceOperation.Failed(Problem(kind)),
        )

    fun <T : Any> absent(): ResourceObservation<T> =
        ResourceObservation(value = null, operation = ResourceOperation.Idle)
}

class FakePostsQueries(
    feed: ResourceObservation<PostFeed> = ResourceObservationFixtures.idle(PostsFixtures.feed()),
    posts: Map<PostId, ResourceObservation<Post>> =
        PostsFixtures.feed().posts.associate { it.id to ResourceObservationFixtures.idle(it) },
) : PostsQueries {
    val feed = MutableStateFlow(feed)
    val postFlows = posts.mapValuesTo(mutableMapOf()) { MutableStateFlow(it.value) }
    val observedPostIds = mutableListOf<PostId>()
    var feedObserverCount: Int = 0
        private set

    override fun observeFeed(): Flow<ResourceObservation<PostFeed>> {
        feedObserverCount++
        return feed
    }

    override fun observePost(id: PostId): Flow<ResourceObservation<Post>> {
        observedPostIds += id
        return postFlows.getOrPut(id) { MutableStateFlow(ResourceObservation.initial()) }
    }

    fun emitPost(id: PostId, observation: ResourceObservation<Post>) {
        postFlows.getOrPut(id) { MutableStateFlow(ResourceObservation.initial()) }.value =
            observation
    }
}

class FakePostsCommands : PostsCommands {
    var feedRefreshCount: Int = 0
        private set

    val postRefreshes = mutableListOf<PostId>()
    var onRefreshFeed: suspend (RefreshQos) -> Outcome<Unit> = { Outcome.Completed(Unit) }
    var onRefreshPost: suspend (PostId, RefreshQos) -> Outcome<Unit> = { _, _ ->
        Outcome.Completed(Unit)
    }

    override suspend fun refreshFeed(qos: RefreshQos): Outcome<Unit> {
        feedRefreshCount++
        return onRefreshFeed(qos)
    }

    override suspend fun refreshPost(id: PostId, qos: RefreshQos): Outcome<Unit> {
        postRefreshes += id
        return onRefreshPost(id, qos)
    }
}
