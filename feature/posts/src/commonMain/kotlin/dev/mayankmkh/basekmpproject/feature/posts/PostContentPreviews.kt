package dev.mayankmkh.basekmpproject.feature.posts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.mayankmkh.basekmpproject.ui.designsystem.theme.BaseKmpProjectTheme

@Preview
@Composable
internal fun PostDetailContentPreview() {
    PostsPreview { PostDetailContent(PostsFeatureFixtures.detail, {}) }
}

@Preview
@Composable
internal fun PostDetailLoadingPreview() {
    PostsPreview { PostDetailContent(PostsFeatureFixtures.loadingDetail, {}) }
}

@Preview
@Composable
internal fun PostDetailOfflinePreview() {
    PostsPreview { PostDetailContent(PostsFeatureFixtures.offlineDetail, {}) }
}

@Preview
@Composable
internal fun PostDetailFailurePreview() {
    PostsPreview { PostDetailContent(PostsFeatureFixtures.failedDetail, {}) }
}

@Preview
@Composable
internal fun PostFeedContentPreview() {
    PostsPreview { PostFeedContent(PostsFeatureFixtures.feed, {}) }
}

@Preview
@Composable
internal fun PostFeedOfflinePreview() {
    PostsPreview { PostFeedContent(PostsFeatureFixtures.offlineFeed, {}) }
}

@Composable
private fun PostsPreview(content: @Composable () -> Unit) {
    BaseKmpProjectTheme(content = content)
}
