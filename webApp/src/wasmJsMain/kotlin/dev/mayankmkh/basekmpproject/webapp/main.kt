package dev.mayankmkh.basekmpproject.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.mayankmkh.basekmpproject.shared.app.App
import dev.mayankmkh.basekmpproject.shared.app.di.initKoin
import dev.mayankmkh.basekmpproject.shared.app.nav.DefaultRootComponent

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()

    val lifecycle = LifecycleRegistry()
    val root =
        DefaultRootComponent(componentContext = DefaultComponentContext(lifecycle = lifecycle))

    // There is no window state to mirror here the way the desktop entry point does: a tab is either
    // loaded or gone, and the page teardown takes the whole Kotlin runtime with it. So the tree is
    // resumed once and never moves again.
    lifecycle.resume()

    ComposeViewport {
        App(root)
    }
}
