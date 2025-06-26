package dev.mayankmkh.basekmpproject.shared.app

import androidx.compose.ui.window.ComposeUIViewController
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent

@Suppress("FunctionNaming")
fun MainViewController(root: RootComponent) = ComposeUIViewController { App(root) }
