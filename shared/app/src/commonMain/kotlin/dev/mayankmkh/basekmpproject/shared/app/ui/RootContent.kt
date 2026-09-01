package dev.mayankmkh.basekmpproject.shared.app.ui

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
import dev.mayankmkh.basekmpproject.shared.app.nav.DetailsRoute
import dev.mayankmkh.basekmpproject.shared.app.nav.ListRoute
import dev.mayankmkh.basekmpproject.shared.features.details.ui.DetailsScreen
import dev.mayankmkh.basekmpproject.shared.features.list.ui.ListScreen

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
    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<ListRoute> {
            ListScreen(
                onItemSelect = { itemId ->
                    val destination = DetailsRoute(itemId)
                    if (backStack.lastOrNull() != destination) backStack += destination
                }
            )
        }
        entry<DetailsRoute> { route ->
            DetailsScreen(
                itemId = route.itemId,
                onBack = { backStack.removeLastOrNull() },
            )
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
        modifier = modifier,
    )
}
