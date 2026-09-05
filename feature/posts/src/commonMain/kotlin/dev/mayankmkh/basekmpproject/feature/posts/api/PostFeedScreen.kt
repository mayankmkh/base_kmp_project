package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedContent
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedUiCommand
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedViewModel
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.ui.designsystem.messageResource
import org.jetbrains.compose.resources.getString
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

    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is PostFeedUiCommand.ShowRefreshFailed ->
                snackbarHostState.showSnackbar(getString(command.kind.messageResource()))
        }
    }

    PostFeedContent(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
