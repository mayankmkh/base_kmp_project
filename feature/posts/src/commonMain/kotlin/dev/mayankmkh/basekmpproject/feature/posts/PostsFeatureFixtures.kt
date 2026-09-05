package dev.mayankmkh.basekmpproject.feature.posts

import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind

internal object PostsFeatureFixtures {
    val posts =
        listOf(
            Post(PostId(1), 10, "First post", "First body"),
            Post(PostId(2), 20, "Second post", "Second body"),
        )
    val feed = PostFeedState(posts = posts, isInitialLoading = false)
    val detail = PostDetailState(post = posts.first(), isInitialLoading = false)
    val loadingDetail = PostDetailState()
    val offlineDetail = detail.copy(problem = Problem(ProblemKind.OFFLINE))
    val failedDetail =
        PostDetailState(
            isInitialLoading = false,
            problem = Problem(ProblemKind.SERVER),
        )
    val offlineFeed = feed.copy(problem = Problem(ProblemKind.OFFLINE))
}
