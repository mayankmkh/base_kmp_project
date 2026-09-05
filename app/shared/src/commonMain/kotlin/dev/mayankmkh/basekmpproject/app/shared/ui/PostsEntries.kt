@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.mayankmkh.basekmpproject.app.shared.nav.AppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.PostFeedRoute
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailOutput
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailScreen
import dev.mayankmkh.basekmpproject.feature.posts.api.PostFeedOutput
import dev.mayankmkh.basekmpproject.feature.posts.api.PostFeedScreen
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey

private const val PostsSceneKey = "posts-list-detail"

internal fun EntryProviderScope<NavKey>.postsEntries(navigationState: AppNavigationState) {
    entry<PostFeedRoute>(metadata = ListDetailSceneStrategy.listPane(sceneKey = PostsSceneKey)) {
        PostFeedEntry(navigationState)
    }
    entry<PostDetailRoute>(
        metadata = ListDetailSceneStrategy.detailPane(sceneKey = PostsSceneKey)
    ) { route ->
        PostDetailEntry(route, navigationState)
    }
}

@Composable
private fun PostFeedEntry(navigationState: AppNavigationState) {
    PostFeedScreen(
        instanceKey = FeatureInstanceKey.forScreen("posts/feed", "post-feed"),
        onOutput = { output ->
            when (output) {
                is PostFeedOutput.OpenPost ->
                    navigationState.navigate(PostDetailRoute(output.id.value))
            }
        },
    )
}

@Composable
private fun PostDetailEntry(route: PostDetailRoute, navigationState: AppNavigationState) {
    PostDetailScreen(
        postId = PostId(route.id),
        instanceKey = FeatureInstanceKey.forScreen("posts/detail/${route.id}", "post-detail"),
        onOutput = { output ->
            when (output) {
                PostDetailOutput.Back -> navigationState.goBack()
            }
        },
    )
}
