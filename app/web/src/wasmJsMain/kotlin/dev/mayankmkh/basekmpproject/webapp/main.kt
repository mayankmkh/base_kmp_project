package dev.mayankmkh.basekmpproject.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentParameters
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.PostFeedRoute
import dev.mayankmkh.basekmpproject.app.shared.rememberAppBackStack

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    ComposeViewport {
        val backStack = rememberAppBackStack()
        ChronologicalBrowserNavigation(
            backStack = backStack,
            saveKey = { route ->
                when (route) {
                    PostFeedRoute -> buildBrowserHistoryFragment("list")
                    is PostDetailRoute ->
                        buildBrowserHistoryFragment(
                            name = "details",
                            parameters = mapOf("id" to route.id.toString()),
                        )
                    else -> null
                }
            },
            restoreKey = { fragment ->
                when (getBrowserHistoryFragmentName(fragment)) {
                    "list" -> PostFeedRoute
                    "details" ->
                        getBrowserHistoryFragmentParameters(fragment)["id"]
                            ?.toLongOrNull()
                            ?.let(::PostDetailRoute)
                    else -> null
                }
            },
        )

        App(backStack = backStack)
    }
}
