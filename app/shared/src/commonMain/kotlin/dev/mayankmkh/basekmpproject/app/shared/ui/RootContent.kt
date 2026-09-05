package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mayankmkh.basekmpproject.app.shared.nav.AppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.nav.StartDestination
import dev.mayankmkh.basekmpproject.app.shared.nav.TopDestination

/**
 * Hosts one fixed [NavDisplay] while Material 3 chooses the destination chrome.
 *
 * Each destination's stack is decorated on its own so its saveable state and ViewModels survive a
 * tab switch. The display receives the home entries followed by the selected non-home destination,
 * matching the Navigation 3 "exit through home" multiple-back-stacks recipe. Keeping the display at
 * one composition position is what lets a window resize change only the chrome.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun RootContent(
    navigationState: AppNavigationState,
    navigationSuiteType: NavigationSuiteType,
    modifier: Modifier = Modifier,
) {
    val viewModelStoreProvider =
        rememberViewModelStoreProvider(parent = LocalViewModelStoreOwner.current)
    val entryProvider = remember(navigationState) { createEntryProvider(navigationState) }
    val entries =
        TopDestination.entries.associateWith { destination ->
            rememberDecoratedNavEntries(
                backStack = navigationState.backStack(destination),
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(viewModelStoreProvider),
                    ),
                entryProvider = entryProvider,
            )
        }
    val selected = navigationState.topDestination
    val displayedEntries =
        entries.getValue(StartDestination) +
            if (selected == StartDestination) emptyList() else entries.getValue(selected)
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()

    NavigationSuiteScaffold(
        navigationItems = {
            TopDestination.entries.forEach { destination ->
                NavigationSuiteItem(
                    selected = destination == selected,
                    onClick = { navigationState.navigateTop(destination) },
                    icon = { Text(destination.label().take(1)) },
                    label = { Text(destination.label()) },
                    navigationSuiteType = navigationSuiteType,
                )
            }
        },
        navigationSuiteType = navigationSuiteType,
        modifier = modifier,
    ) {
        // The pinned Navigation 3 UI `entries` overload installs its scene-setup and lifecycle
        // decorators itself; the stacks above carry the public saveable-state and ViewModel ones.
        NavDisplay(
            entries = displayedEntries,
            onBack = navigationState::goBack,
            sceneStrategies = listOf(listDetailSceneStrategy),
        )
    }
}

private fun TopDestination.label(): String =
    when (this) {
        TopDestination.POSTS -> "Posts"
        TopDestination.TODOS -> "Todos"
    }
