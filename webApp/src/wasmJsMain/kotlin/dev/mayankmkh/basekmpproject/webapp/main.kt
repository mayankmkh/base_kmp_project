package dev.mayankmkh.basekmpproject.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.webhistory.withWebHistory
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import dev.mayankmkh.basekmpproject.shared.app.App
import dev.mayankmkh.basekmpproject.shared.app.di.initKoin
import dev.mayankmkh.basekmpproject.shared.app.nav.DefaultRootComponent
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class, ExperimentalDecomposeApi::class)
fun main() {
    initKoin()

    val lifecycle = LifecycleRegistry()

    // Binds the stack to the browser history: the state keeper restores the tree across reloads and
    // `deepLink` carries the URL the tab was opened with.
    val root = withWebHistory { stateKeeper, deepLink ->
        DefaultRootComponent(
            componentContext =
                DefaultComponentContext(lifecycle = lifecycle, stateKeeper = stateKeeper),
            deepLinkUrl = deepLink,
        )
    }

    lifecycle.attachToDocument()

    ComposeViewport {
        App(root)
    }
}

// A backgrounded tab keeps running, so without this the tree stays resumed and its collectors and
// timers keep working while nothing is on screen.
private fun LifecycleRegistry.attachToDocument() {
    fun onVisibilityChanged() {
        if (isDocumentHidden()) stop() else resume()
    }

    onVisibilityChanged()
    document.addEventListener(type = "visibilitychange", callback = { onVisibilityChanged() })
}

// `kotlinx-browser` binds neither `Document.hidden` nor `Document.visibilityState`.
private fun isDocumentHidden(): Boolean = js("document.hidden")
