package dev.mayankmkh.basekmpproject.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentParameters
import dev.mayankmkh.basekmpproject.shared.app.App
import dev.mayankmkh.basekmpproject.shared.app.di.initKoin
import dev.mayankmkh.basekmpproject.shared.app.nav.DetailsRoute
import dev.mayankmkh.basekmpproject.shared.app.nav.ListRoute
import dev.mayankmkh.basekmpproject.shared.app.rememberAppBackStack

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    ComposeViewport {
        val backStack = rememberAppBackStack()
        ChronologicalBrowserNavigation(
            backStack = backStack,
            saveKey = { route ->
                when (route) {
                    ListRoute -> buildBrowserHistoryFragment("list")
                    is DetailsRoute ->
                        buildBrowserHistoryFragment(
                            name = "details",
                            parameters = mapOf("id" to route.itemId),
                        )
                    else -> null
                }
            },
            restoreKey = { fragment ->
                when (getBrowserHistoryFragmentName(fragment)) {
                    "list" -> ListRoute
                    "details" ->
                        getBrowserHistoryFragmentParameters(fragment)["id"]?.let(::DetailsRoute)
                    else -> null
                }
            },
        )

        App(backStack = backStack)
    }
}
