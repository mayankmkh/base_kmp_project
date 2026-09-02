package dev.mayankmkh.basekmpproject.feature.posts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory

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
        topBar = { TopAppBar(title = { Text("Posts") }) },
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
                if (state.isStale) {
                    StaleBanner(state.problem)
                }
                Box(Modifier.fillMaxSize()) {
                    when {
                        state.isInitialLoading ->
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.posts.isNotEmpty() ->
                            LazyColumn(contentPadding = padding) {
                                items(state.posts, key = { it.id.value }) { post ->
                                    Column {
                                        Text(
                                            text = post.title,
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .clickable {
                                                        onAction(PostFeedAction.OpenPost(post.id))
                                                    }
                                                    .padding(16.dp),
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        state.problem != null ->
                            Failure(state.problem, { onAction(PostFeedAction.Refresh) })
                        else -> Centred { Text("Nothing here yet. Pull down to refresh.") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PostDetailContent(
    state: PostDetailState,
    onAction: (PostDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (state.isStale) {
            StaleBanner(state.problem)
        }
        Box(Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.post != null ->
                    Text(
                        text = state.post.body,
                        modifier =
                            Modifier.fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                    )
                state.problem != null ->
                    Failure(state.problem, { onAction(PostDetailAction.Retry) })
            }
        }
    }
}

@Composable
private fun StaleBanner(problem: ResourceProblem?) {
    val message =
        if (problem?.category == ResourceProblemCategory.OFFLINE) {
            "Offline — showing saved posts"
        } else {
            "Showing saved posts"
        }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Failure(problem: ResourceProblem, onRetry: () -> Unit) {
    Centred {
        Text(
            text = problem.userMessage(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}

internal fun ResourceProblem.userMessage(): String =
    when (category) {
        ResourceProblemCategory.OFFLINE -> "You're offline"
        ResourceProblemCategory.TEMPORARY -> "Posts are temporarily unavailable"
        ResourceProblemCategory.ACCESS -> "You don't have access to these posts"
        ResourceProblemCategory.PERMANENT -> "This post is unavailable"
        ResourceProblemCategory.UNKNOWN -> "Could not refresh posts"
    }
