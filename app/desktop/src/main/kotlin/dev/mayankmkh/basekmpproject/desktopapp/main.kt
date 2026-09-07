package dev.mayankmkh.basekmpproject.desktopapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.di.shutdownKoin

fun main() {

    // jpackage stamps `jpackage.app-path` into every launcher it builds, so its absence means the
    // app is running from Gradle or the IDE rather than an installed distribution.
    val isDebug = System.getProperty("jpackage.app-path") == null

    // Compose Desktop has no flavors, so the environment travels as a system property the build
    // script pins on both the `run` task and every packaged launcher's JVM arguments. A build that
    // said nothing is a production build; nothing here silently downgrades to staging.
    val environmentId = System.getProperty(EnvironmentProperty) ?: BuildEnvironment.Production.id

    initKoin(isDebug = isDebug, environment = BuildEnvironment.fromId(environmentId))

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

/** Set by `app/desktop/build.gradle.kts`; the name is duplicated there and nowhere else. */
private const val EnvironmentProperty = "bkp.environment"
