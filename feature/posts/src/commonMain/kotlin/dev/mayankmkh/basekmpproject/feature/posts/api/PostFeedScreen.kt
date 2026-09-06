package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedContent
import dev.mayankmkh.basekmpproject.feature.posts.PostFeedViewModel
import dev.mayankmkh.basekmpproject.feature.posts.PostsUiCommand
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.presentation.featureViewModel
import dev.mayankmkh.basekmpproject.ui.designsystem.messageResource
import org.jetbrains.compose.resources.getString

/** Cell type of [PostFeedScreen]. */
public const val PostFeedCellType: String = "post-feed"

@Composable
public fun PostFeedScreen(
    instanceKey: FeatureInstanceKey,
    onOutput: (PostFeedOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PostFeedViewModel = featureViewModel(instanceKey)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is PostsUiCommand.ShowRefreshFailed ->
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
