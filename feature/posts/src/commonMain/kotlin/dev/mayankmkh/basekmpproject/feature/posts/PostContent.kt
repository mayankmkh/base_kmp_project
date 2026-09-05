package dev.mayankmkh.basekmpproject.feature.posts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import base_kmp_project.feature.posts.generated.resources.Res
import base_kmp_project.feature.posts.generated.resources.nothing_here_yet
import base_kmp_project.feature.posts.generated.resources.post_unavailable
import base_kmp_project.feature.posts.generated.resources.posts
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.ui.designsystem.Centred
import dev.mayankmkh.basekmpproject.ui.designsystem.Failure
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostFeedContent(
    state: PostFeedState,
    onAction: (PostFeedAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val pullState = rememberPullToRefreshState()
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.posts)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(PostFeedAction.Refresh) },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.isRefreshing,
                    modifier =
                        Modifier.align(Alignment.TopCenter)
                            .padding(top = padding.calculateTopPadding()),
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                when {
                    state.isInitialLoading ->
                        Centred(Modifier.fillMaxSize()) { CircularProgressIndicator() }
                    state.posts.isNotEmpty() ->
                        LazyColumn(contentPadding = padding) {
                            items(state.posts, key = { it.id.value }) { post ->
                                PostRow(post) { onAction(PostFeedAction.OpenPost(post.id)) }
                            }
                        }
                    state.problem != null ->
                        Failure(
                            problem = state.problem,
                            onRetry = { onAction(PostFeedAction.Refresh) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    else ->
                        Centred(Modifier.fillMaxSize()) {
                            Text(stringResource(Res.string.nothing_here_yet))
                        }
                }
            }
        }
    }
}

@Composable
private fun PostRow(post: Post, onOpen: () -> Unit) {
    Column {
        Text(
            text = post.title,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp),
        )
        HorizontalDivider()
    }
}

@Composable
internal fun PostDetailContent(
    state: PostDetailState,
    onAction: (PostDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        when {
            state.isInitialLoading -> Centred { CircularProgressIndicator() }
            state.post != null ->
                Text(
                    text = state.post.body,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            state.problem != null -> Failure(state.problem, { onAction(PostDetailAction.Retry) })
            state.isAbsent -> Centred { Text(stringResource(Res.string.post_unavailable)) }
        }
    }
}
