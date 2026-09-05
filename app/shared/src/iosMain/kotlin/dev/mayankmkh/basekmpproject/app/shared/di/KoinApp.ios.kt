package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.scope.Scope

/** The Swift entry point's `doInitKoin()`: the debug binary is what an Xcode Debug build links. */
@OptIn(ExperimentalNativeApi::class) fun initKoin() = initKoin(isDebug = Platform.isDebugBinary)

// Kermit's Apple default is an OSLog writer tuned for Xcode: emoji severity prefixes, stack traces
// on stdout and no subsystem. Release names the subsystem so Console and `log stream` can filter
// the app's lines and keep stack traces in the unified log; messages stay `<private>` because the
// writer does not opt into public logging.
internal actual fun appLogWriter(isDebug: Boolean): LogWriter =
    if (isDebug) platformLogWriter() else OSLogWriter(subsystem = ApplicationId)

internal actual fun Scope.createPlatformContext(): PlatformContext = PlatformContext(ApplicationId)
