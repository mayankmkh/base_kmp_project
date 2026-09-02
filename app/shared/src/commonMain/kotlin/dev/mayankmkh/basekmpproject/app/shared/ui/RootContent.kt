package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.PostFeedRoute
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailOutput
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailScreen
import dev.mayankmkh.basekmpproject.feature.posts.api.PostFeedOutput
import dev.mayankmkh.basekmpproject.feature.posts.api.PostFeedScreen
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey

@Composable
internal fun RootContent(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    val viewModelStoreProvider =
        rememberViewModelStoreProvider(parent = LocalViewModelStoreOwner.current)
    val entryDecorators: List<NavEntryDecorator<NavKey>> =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(viewModelStoreProvider),
        )
    // The web build can restore a session straight onto a detail route, which makes that route the
    // whole back stack. Popping it would leave `NavDisplay` with nothing to display, so a one-entry
    // stack falls back to the feed instead of emptying.
    val pop: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else if (backStack.singleOrNull() != PostFeedRoute) {
            backStack.clear()
            backStack += PostFeedRoute
        }
    }
    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<PostFeedRoute> {
            PostFeedScreen(
                instanceKey =
                    FeatureInstanceKey.forScreen(
                        route = "posts/feed",
                        cellType = "post-feed",
                    ),
                onOutput = { output ->
                    when (output) {
                        is PostFeedOutput.OpenPost -> {
                            val destination = PostDetailRoute(output.id.value)
                            if (backStack.lastOrNull() != destination) backStack += destination
                        }
                    }
                },
            )
        }
        entry<PostDetailRoute> { route ->
            PostDetailScreen(
                postId = PostId(route.id),
                instanceKey =
                    FeatureInstanceKey.forScreen(
                        route = "posts/detail/${route.id}",
                        cellType = "post-detail",
                    ),
                onOutput = { output ->
                    when (output) {
                        PostDetailOutput.Back -> pop()
                    }
                },
            )
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
        modifier = modifier,
    )
}
