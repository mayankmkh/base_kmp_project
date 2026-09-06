package dev.mayankmkh.basekmpproject.feature.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailOutput
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.failure
import dev.mayankmkh.basekmpproject.foundation.resource.hasValue
import dev.mayankmkh.basekmpproject.foundation.resource.isAbsent
import dev.mayankmkh.basekmpproject.foundation.resource.isInitialLoading
import dev.mayankmkh.basekmpproject.foundation.resource.isRefreshing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class PostDetailState(
    val post: Post? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val problem: Problem? = null,
    val isAbsent: Boolean = false,
)

internal sealed interface PostDetailAction {
    data object Retry : PostDetailAction

    data object Back : PostDetailAction
}

internal class PostDetailViewModel(
    val postId: PostId,
    queries: PostsQueries,
    private val commands: PostsCommands,
) : ViewModel() {
    private val uiCommandChannel = Channel<PostsUiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<PostDetailOutput>(Channel.BUFFERED)

    val uiCommands: Flow<PostsUiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<PostDetailOutput> = outputChannel.receiveAsFlow()
    val state: StateFlow<PostDetailState> =
        queries
            .observePost(postId)
            .map(ResourceObservation<Post>::toDetailState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PostDetailState(),
            )

    fun onAction(action: PostDetailAction) {
        when (action) {
            PostDetailAction.Retry ->
                viewModelScope.launch {
                    val outcome = commands.refreshPost(postId)
                    if (outcome is Outcome.Failed) {
                        uiCommandChannel.send(
                            PostsUiCommand.ShowRefreshFailed(outcome.problem.kind)
                        )
                    }
                }
            PostDetailAction.Back ->
                viewModelScope.launch { outputChannel.send(PostDetailOutput.Back) }
        }
    }
}

private fun ResourceObservation<Post>.toDetailState() =
    PostDetailState(
        post = value,
        isInitialLoading = isInitialLoading,
        isRefreshing = hasValue && isRefreshing,
        problem = failure,
        isAbsent = isAbsent,
    )
