package dev.mayankmkh.basekmpproject.shared.features.list.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ItemsModel
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ListScreen(onItemSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ListViewModel = koinViewModel()
    val currentOnItemSelect by rememberUpdatedState(onItemSelect)
    // Owned here rather than in `ListContent`: the events are a single-consumer channel, so all of
    // them have to be collected in one place, and this is the composable that has one.
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventsFlow.collect { event ->
            when (event) {
                is ListViewModel.Event.ItemClicked -> currentOnItemSelect(event.item)
                is ListViewModel.Event.RefreshFailed ->
                    snackbarHostState.showSnackbar("Could not refresh: ${event.error.message}")
            }
        }
    }

    ListContent(viewModel = viewModel, snackbarHostState = snackbarHostState, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListContent(
    viewModel: ListViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiState by viewModel.uiStateFlow.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(text = "Navigation 3 Sample") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        // Wraps every state, not just the populated one, so a failed first load can be retried by
        // pulling as well as by the button.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                // The default indicator hangs off the top of the box, which here is the top of the
                // window; the inset padding is what drops it below the app bar.
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier =
                        Modifier.align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                )
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                when (val viewState = uiState) {
                    UiState.Initial -> Initial(Modifier.padding(paddingValues))
                    UiState.InProgress -> InProgress(Modifier.padding(paddingValues))
                    // The list gets the insets as content padding rather than as a margin, so its
                    // items scroll under the system bars instead of stopping at them.
                    is UiState.Success ->
                        Success(
                            viewState.data,
                            onItemClick = viewModel::onItemClicked,
                            contentPadding = paddingValues,
                        )
                    is UiState.Failure ->
                        Failure(
                            viewState.error,
                            onRetry = viewModel::onRefresh,
                            modifier = Modifier.padding(paddingValues),
                        )
                }
            }
        }
    }
}

@Composable private fun Initial(modifier: Modifier = Modifier) = Text("Initializing", modifier)

@Composable
private fun InProgress(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier)
}

@Composable
private fun Success(
    itemsModel: ItemsModel,
    onItemClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (itemsModel.items.isEmpty()) {
        Empty(modifier.padding(contentPadding))
        return
    }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        items(itemsModel.items, key = { it.id }) { item ->
            Column {
                Text(
                    text = item.title,
                    modifier =
                        Modifier.fillMaxWidth().clickable { onItemClick(item.id) }.padding(16.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * What a successful fetch of nothing looks like.
 *
 * Distinct from [Failure]: the request worked and the feed is genuinely empty, so there is nothing
 * to retry and no error to explain.
 */
@Composable
private fun Empty(modifier: Modifier = Modifier) {
    Centred(modifier) { Text("Nothing here yet. Pull down to refresh.") }
}

@Composable
private fun Failure(throwable: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Centred(modifier) {
        Text(
            text = "Error $throwable",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Centred(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}
