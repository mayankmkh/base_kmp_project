package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.feature.posts.PostDetailContent
import dev.mayankmkh.basekmpproject.feature.posts.PostDetailUiCommand
import dev.mayankmkh.basekmpproject.feature.posts.PostDetailViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
public fun PostDetailCell(
    postId: PostId,
    instanceKey: FeatureInstanceKey,
    onOutput: (PostDetailOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    PostDetailCell(postId, instanceKey, onOutput, PaddingValues(), modifier)
}

@Composable
internal fun PostDetailCell(
    postId: PostId,
    instanceKey: FeatureInstanceKey,
    onOutput: (PostDetailOutput) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viewModel: PostDetailViewModel =
        koinViewModel(
            key = instanceKey.value,
            parameters = { parametersOf(postId, instanceKey) },
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
                        is PostDetailUiCommand.ShowRefreshFailed ->
                            snackbarHostState.showSnackbar(command.message)
                    }
                }
            }
        }
    }

    Box(modifier.fillMaxSize().padding(contentPadding)) {
        PostDetailContent(state = state, onAction = viewModel::onAction)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
