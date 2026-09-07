package dev.mayankmkh.basekmpproject.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.nav.restoreAppRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.saveAppRoute
import dev.mayankmkh.basekmpproject.app.shared.rememberAppBackStack
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Development webpack serves localhost; deployed production bundles use their real host.
    initKoin(
        isDebug = window.location.hostname == "localhost",
        environment = BuildEnvironment.fromId(hostPageEnvironmentId()),
    )
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

/**
 * The web build has no flavors and no build configurations: one bundle is served from every
 * deployment, and which one it is belongs to the page that mounts it, not to webpack. The host page
 * declares it, and a page that declares nothing is a production page.
 *
 * `<meta name="app-environment" content="staging">`
 */
private fun hostPageEnvironmentId(): String =
    document.querySelector("meta[name=\"$EnvironmentMetaName\"]")?.getAttribute("content")?.takeIf {
        it.isNotBlank()
    } ?: BuildEnvironment.Production.id

private const val EnvironmentMetaName = "app-environment"
