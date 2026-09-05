package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import org.koin.core.scope.Scope

// `MainApplication` passes `BuildConfig.DEBUG`, which is the truthful signal and the only one a
// library module cannot reach: it has no `BuildConfig`, and the manifest's debuggable flag needs a
// `Context` that does not exist until the graph it configures has started. An entry point that
// forgets to pass it gets the quieter of the two configurations, never the louder one.
internal actual fun platformIsDebugBuild(): Boolean = false

internal actual fun appLogWriter(isDebug: Boolean): LogWriter = platformLogWriter()

internal actual fun Scope.createPlatformContext(): PlatformContext =
    PlatformContext(get(), ApplicationId)
