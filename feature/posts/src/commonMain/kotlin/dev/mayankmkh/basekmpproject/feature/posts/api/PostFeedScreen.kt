package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedContent
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedUiCommand
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
public fun PostFeedScreen(
    instanceKey: FeatureInstanceKey,
    onOutput: (PostFeedOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PostFeedViewModel =
        koinViewModel(
            key = instanceKey.value,
            parameters = { parametersOf(instanceKey) },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnOutput by rememberUpdatedState(onOutput)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.outputs.collect { currentOnOutput(it) } }
            launch {
                viewModel.uiCommands.collect { command ->
                    when (command) {
                        is PostFeedUiCommand.ShowRefreshFailed ->
                            snackbarHostState.showSnackbar(command.message)
                    }
                }
            }
        }
    }

    PostFeedContent(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
