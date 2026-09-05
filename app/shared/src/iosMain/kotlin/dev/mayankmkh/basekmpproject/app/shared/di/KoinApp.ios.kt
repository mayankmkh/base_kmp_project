package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.scope.Scope

// True for the framework's debug binary, which is what an Xcode Debug configuration links.
@OptIn(ExperimentalNativeApi::class)
internal actual fun platformIsDebugBuild(): Boolean = Platform.isDebugBinary

// Kermit's Apple default is an OSLog writer tuned for Xcode: emoji severity prefixes and stack
// traces printed to stdout, which the Xcode console shows but Console and `log stream` never see,
// and no subsystem. Debug builds keep it. Release builds name the subsystem so Console and
// `log stream` can filter the app's lines, keep stack traces in the unified log, and stay
// `<private>`
// in captures because the writer does not opt into public logging. The tag is part of the formatted
// message, so the category stays empty.
internal actual fun appLogWriter(isDebug: Boolean): LogWriter =
    if (isDebug) platformLogWriter() else OSLogWriter(subsystem = ApplicationId)

internal actual fun Scope.createPlatformContext(): PlatformContext = PlatformContext(ApplicationId)
