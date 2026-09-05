package dev.mayankmkh.basekmpproject.app.shared.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable

/** A top-level destination: one tab, one root route, one back stack. The first entry is home. */
@Serializable
internal enum class TopDestination(val rootRoute: NavKey) {
    POSTS(PostFeedRoute),
    TODOS(TodoListRoute);

    fun owns(key: NavKey): Boolean =
        when (this) {
            POSTS -> key is PostsRoute
            TODOS -> key is TodosRoute
        }
}

internal val StartDestination: TopDestination = TopDestination.entries.first()

/**
 * Owns one saved Navigation 3 back stack per [TopDestination], following the multiple-back-stacks
 * recipe: the display shows the home stack followed by the selected destination's stack, and Back
 * on a non-home root returns home.
 *
 * The browser bridge stack is derived from this state in [rememberAppNavigationState]; browser
 * Back, Forward and direct URL entry write into that bridge and are folded back here.
 */
@Stable
internal class AppNavigationState(
    private val topDestinationState: MutableState<TopDestination>,
    private val stacks: Map<TopDestination, NavBackStack<NavKey>>,
) {
    internal var topDestination: TopDestination by topDestinationState
        private set

    internal fun backStack(destination: TopDestination): NavBackStack<NavKey> =
        stacks.getValue(destination)

    internal fun navigateTop(destination: TopDestination) {
        topDestination = destination
    }

    /**
     * Shows [route] on the stack of the destination that owns it and selects that destination. A
     * stack holds at most one entry of each route class, so opening a second detail replaces the
     * first. [replaceTop] pops the current entry first, for flows such as editor to created item.
     */
    internal fun navigate(route: NavKey, replaceTop: Boolean = false) {
        val destination = route.topDestination()
        val stack = backStack(destination)
        if (replaceTop) stack.removeLastOrNull()
        stack.removeAll { it::class == route::class }
        stack += route
        topDestination = destination
    }

    internal fun goBack() {
        val stack = backStack(topDestination)
        when {
            stack.size > 1 -> stack.removeLastOrNull()
            topDestination != StartDestination -> topDestination = StartDestination
        }
    }

    internal fun flattenedKeys(): List<NavKey> {
        val home = backStack(StartDestination).toList()
        return if (topDestination == StartDestination) home
        else home + backStack(topDestination).toList()
    }

    internal fun restoreFromBrowser(keys: List<NavKey>) {
        if (keys.isEmpty() || keys == flattenedKeys()) return
        partition(keys).forEach { (destination, routes) ->
            if (routes.isNotEmpty()) backStack(destination).replaceWith(routes)
        }
        topDestination = keys.last().topDestination()
    }
}

@Composable
internal fun rememberAppNavigationState(
    browserBackStack: NavBackStack<NavKey>,
    configuration: SavedStateConfiguration,
): AppNavigationState {
    val initial = remember(browserBackStack) { partition(browserBackStack.toList()) }
    val stacks =
        TopDestination.entries.associateWith { destination ->
            val routes = initial.getValue(destination).ifEmpty { listOf(destination.rootRoute) }
            rememberNavBackStack(configuration, elements = routes.toTypedArray())
        }
    val topDestinationState =
        rememberSerializable(
            stateSerializer = TopDestination.serializer(),
            configuration = configuration,
        ) {
            mutableStateOf(browserBackStack.lastOrNull()?.topDestination() ?: StartDestination)
        }
    val state =
        remember(topDestinationState, stacks) { AppNavigationState(topDestinationState, stacks) }
    LaunchedEffect(state, browserBackStack) {
        snapshotFlow { state.flattenedKeys() }
            .collect { flattened ->
                if (browserBackStack.toList() != flattened) browserBackStack.replaceWith(flattened)
            }
    }
    LaunchedEffect(state, browserBackStack) {
        snapshotFlow { browserBackStack.toList() }.collect(state::restoreFromBrowser)
    }
    return state
}

/** Splits a flat key list per destination, each non-empty part rooted at its destination's root. */
private fun partition(keys: List<NavKey>): Map<TopDestination, List<NavKey>> =
    TopDestination.entries.associateWith { destination ->
        val owned = keys.filter(destination::owns)
        if (owned.isEmpty()) owned
        else listOf(destination.rootRoute) + owned.filterNot { it == destination.rootRoute }
    }

private fun NavKey.topDestination(): TopDestination = TopDestination.entries.first { it.owns(this) }

private fun NavBackStack<NavKey>.replaceWith(routes: List<NavKey>) {
    clear()
    addAll(routes)
}
