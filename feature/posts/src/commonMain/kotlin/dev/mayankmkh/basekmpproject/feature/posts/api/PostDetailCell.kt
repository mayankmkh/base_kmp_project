package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.feature.posts.PostDetailContent
import dev.mayankmkh.basekmpproject.feature.posts.PostDetailUiCommand
import dev.mayankmkh.basekmpproject.feature.posts.PostDetailViewModel
import dev.mayankmkh.basekmpproject.feature.posts.messageResource
import dev.mayankmkh.basekmpproject.foundation.presentation.CollectWhileStarted
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Independently hostable post detail presentation.
 *
 * A host must supply one stable [instanceKey] for each placement and post. Reusing a placement key
 * for a different [postId] is a host contract violation and fails fast.
 *
 * @param snackbarHostState surface-owned snackbar state. When `null`, the Cell self-hosts a
 *   snackbar; otherwise it sends messages to this state and renders no snackbar host.
 */
@Composable
public fun PostDetailCell(
    postId: PostId,
    instanceKey: FeatureInstanceKey,
    onOutput: (PostDetailOutput) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState? = null,
) {
    val viewModel: PostDetailViewModel =
        koinViewModel(
            key = instanceKey.value,
            parameters = { parametersOf(postId, instanceKey) },
        )
    check(viewModel.postId == postId) {
        "FeatureInstanceKey '${instanceKey.value}' is already bound to post " +
            "${viewModel.postId.value}; fold the post id into the instance key"
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = snackbarHostState ?: remember { SnackbarHostState() }

    CollectWhileStarted(viewModel.outputs, onOutput)
    CollectWhileStarted(viewModel.uiCommands) { command ->
        when (command) {
            is PostDetailUiCommand.ShowRefreshFailed ->
                snackbarHost.showSnackbar(getString(command.category.messageResource()))
        }
    }

    Box(modifier) {
        PostDetailContent(
            state = state,
            onAction = viewModel::onAction,
            modifier = Modifier.padding(contentPadding),
        )
        if (snackbarHostState == null) {
            SnackbarHost(
                snackbarHost,
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}
