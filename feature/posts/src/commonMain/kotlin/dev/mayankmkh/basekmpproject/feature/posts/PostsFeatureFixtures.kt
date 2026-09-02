package dev.mayankmkh.basekmpproject.feature.posts

import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory

internal object PostsFeatureFixtures {
    val posts =
        listOf(
            Post(PostId(1), 10, "First post", "First body"),
            Post(PostId(2), 20, "Second post", "Second body"),
        )
    val feed = PostFeedState(posts = posts, isInitialLoading = false)
    val detail = PostDetailState(post = posts.first(), isInitialLoading = false)
    val offlineFeed =
        feed.copy(
            isStale = true,
            problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true),
        )
}
