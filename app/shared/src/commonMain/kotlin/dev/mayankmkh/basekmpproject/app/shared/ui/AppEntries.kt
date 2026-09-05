package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import dev.mayankmkh.basekmpproject.app.shared.nav.AppNavigationState

internal fun createEntryProvider(
    navigationState: AppNavigationState
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    postsEntries(navigationState)
    todosEntries(navigationState)
}
