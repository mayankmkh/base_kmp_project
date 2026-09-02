package dev.mayankmkh.basekmpproject.feature.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailOutput
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class PostDetailState(
    val post: Post? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val problem: ResourceProblem? = null,
)

internal sealed interface PostDetailAction {
    data object Retry : PostDetailAction

    data object Back : PostDetailAction
}

internal sealed interface PostDetailUiCommand {
    data class ShowRefreshFailed(val message: String) : PostDetailUiCommand
}

internal class PostDetailViewModel(
    private val postId: PostId,
    instanceKey: FeatureInstanceKey,
    queries: PostsQueries,
    private val commands: PostsCommands,
) : ViewModel() {
    private val uiCommandChannel = Channel<PostDetailUiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<PostDetailOutput>(Channel.BUFFERED)

    val uiCommands: Flow<PostDetailUiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<PostDetailOutput> = outputChannel.receiveAsFlow()
    val state: StateFlow<PostDetailState> =
        queries
            .observePost(postId)
            .distinctUntilChanged()
            .onEach { observation ->
                val failure = observation.operation as? ResourceOperation.Failed
                if (failure != null) {
                    uiCommandChannel.send(
                        PostDetailUiCommand.ShowRefreshFailed(failure.problem.userMessage())
                    )
                }
            }
            .map(ResourceObservation<Post>::toDetailState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PostDetailState(),
            )

    init {
        require(instanceKey.value.isNotBlank())
    }

    fun onAction(action: PostDetailAction) {
        when (action) {
            PostDetailAction.Retry -> viewModelScope.launch { commands.refreshPost(postId) }
            PostDetailAction.Back ->
                viewModelScope.launch { outputChannel.send(PostDetailOutput.Back) }
        }
    }
}

private fun ResourceObservation<Post>.toDetailState() =
    PostDetailState(
        post = value,
        isInitialLoading = value == null && operation is ResourceOperation.Refreshing,
        isRefreshing = value != null && operation is ResourceOperation.Refreshing,
        isStale = freshness == ResourceFreshness.STALE,
        problem = (operation as? ResourceOperation.Failed)?.problem,
    )
