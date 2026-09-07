package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.scope.Scope

/**
 * The Swift entry point's `doInitKoin(environmentId:)`.
 *
 * The debug binary is what an Xcode Debug configuration links, so Kotlin can read that signal
 * itself. Which deployment the configuration selected is an Xcode fact and cannot be: Swift reads
 * it from the bundle's `AppEnvironment` key, which the environment's xcconfig stamps into
 * `Info.plist`, and passes the id through here.
 */
@OptIn(ExperimentalNativeApi::class)
fun initKoin(environmentId: String) =
    initKoin(isDebug = Platform.isDebugBinary, environment = BuildEnvironment.fromId(environmentId))

// Kermit's Apple default is an OSLog writer tuned for Xcode: emoji severity prefixes, stack traces
// on stdout and no subsystem. Release names the subsystem so Console and `log stream` can filter
// the app's lines and keep stack traces in the unified log; messages stay `<private>` because the
// writer does not opt into public logging. The subsystem is the environment's application id, so
// a staging and a production install never share a stream.
internal actual fun appLogWriter(isDebug: Boolean, applicationId: String): LogWriter =
    if (isDebug) platformLogWriter() else OSLogWriter(subsystem = applicationId)

internal actual fun Scope.createPlatformContext(): PlatformContext =
    PlatformContext(get<AppEnvironment>().applicationId)
