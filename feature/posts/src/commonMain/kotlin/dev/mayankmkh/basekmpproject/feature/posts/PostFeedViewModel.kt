package dev.mayankmkh.basekmpproject.feature.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mayankmkh.basekmpproject.capability.posts.api.Post
import dev.mayankmkh.basekmpproject.capability.posts.api.PostFeed
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.feature.posts.api.PostFeedOutput
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.failure
import dev.mayankmkh.basekmpproject.foundation.resource.hasValue
import dev.mayankmkh.basekmpproject.foundation.resource.isRefreshing
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

internal data class PostFeedState(
    val posts: List<Post> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val problem: ResourceProblem? = null,
)

internal sealed interface PostFeedAction {
    data object Refresh : PostFeedAction

    data class OpenPost(val id: PostId) : PostFeedAction
}

internal sealed interface PostFeedUiCommand {
    data class ShowRefreshFailed(val message: String) : PostFeedUiCommand
}

internal class PostFeedViewModel(
    instanceKey: FeatureInstanceKey,
    queries: PostsQueries,
    private val commands: PostsCommands,
) : ViewModel() {
    private val uiCommandChannel = Channel<PostFeedUiCommand>(Channel.BUFFERED)
    private val outputChannel = Channel<PostFeedOutput>(Channel.BUFFERED)

    val uiCommands: Flow<PostFeedUiCommand> = uiCommandChannel.receiveAsFlow()
    val outputs: Flow<PostFeedOutput> = outputChannel.receiveAsFlow()
    val state: StateFlow<PostFeedState> =
        queries
            .observeFeed()
            .distinctUntilChanged()
            .onEach { observation ->
                observation.failure?.let { problem ->
                    uiCommandChannel.send(
                        PostFeedUiCommand.ShowRefreshFailed(problem.userMessage())
                    )
                }
            }
            .map { it.toFeedState() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PostFeedState(),
            )

    init {
        require(instanceKey.value.isNotBlank())
    }

    fun onAction(action: PostFeedAction) {
        when (action) {
            PostFeedAction.Refresh -> viewModelScope.launch { commands.refreshFeed() }
            is PostFeedAction.OpenPost ->
                viewModelScope.launch { outputChannel.send(PostFeedOutput.OpenPost(action.id)) }
        }
    }
}

private fun ResourceObservation<PostFeed>.toFeedState() =
    PostFeedState(
        posts = value?.posts.orEmpty(),
        isInitialLoading = !hasValue && isRefreshing,
        isRefreshing = hasValue && isRefreshing,
        isStale = freshness == ResourceFreshness.STALE,
        problem = failure,
    )
