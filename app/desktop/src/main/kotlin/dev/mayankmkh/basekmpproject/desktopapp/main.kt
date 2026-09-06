package dev.mayankmkh.basekmpproject.desktopapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.di.shutdownKoin

fun main() {

    // jpackage stamps `jpackage.app-path` into every launcher it builds, so its absence means the
    // app is running from Gradle or the IDE rather than an installed distribution.
    initKoin(isDebug = System.getProperty("jpackage.app-path") == null)

    application {
        Window(
            onCloseRequest = {
                shutdownKoin()
                exitApplication()
            },
            title = "base_kmp_project",
        ) {
            App()
        }
    }
}
