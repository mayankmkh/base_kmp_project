package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import org.koin.core.scope.Scope

// jpackage stamps `jpackage.app-path` into every launcher it builds, so its absence means the app
// is running from Gradle or the IDE rather than an installed distribution.
internal actual fun platformIsDebugBuild(): Boolean =
    System.getProperty("jpackage.app-path") == null

internal actual fun appLogWriter(isDebug: Boolean): LogWriter = platformLogWriter()

internal actual fun Scope.createPlatformContext(): PlatformContext = PlatformContext(ApplicationId)
