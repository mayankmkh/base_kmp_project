package dev.mayankmkh.basekmpproject.desktopapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mayankmkh.basekmpproject.shared.app.App
import dev.mayankmkh.basekmpproject.shared.app.di.initKoin

fun main() {

    initKoin()

    application {
        Window(onCloseRequest = ::exitApplication, title = "base_kmp_project") {
            App()
        }
    }
}
