package dev.mayankmkh.basekmpproject.desktopapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin

fun main() {

    initKoin()

    application {
        Window(onCloseRequest = ::exitApplication, title = "base_kmp_project") {
            App()
        }
    }
}
