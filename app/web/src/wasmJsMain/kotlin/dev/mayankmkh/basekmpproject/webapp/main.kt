package dev.mayankmkh.basekmpproject.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.nav.restoreAppRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.saveAppRoute
import dev.mayankmkh.basekmpproject.app.shared.rememberAppBackStack
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Development webpack serves localhost; deployed production bundles use their real host.
    initKoin(isDebug = window.location.hostname == "localhost")
    ComposeViewport {
        val backStack = rememberAppBackStack()
        ChronologicalBrowserNavigation(
            backStack = backStack,
            saveKey = ::saveAppRoute,
            restoreKey = ::restoreAppRoute,
        )

        App(backStack = backStack)
    }
}
