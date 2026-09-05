package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import org.koin.core.scope.Scope

internal actual fun appLogWriter(isDebug: Boolean): LogWriter = platformLogWriter()

internal actual fun Scope.createPlatformContext(): PlatformContext =
    PlatformContext(get(), ApplicationId)
