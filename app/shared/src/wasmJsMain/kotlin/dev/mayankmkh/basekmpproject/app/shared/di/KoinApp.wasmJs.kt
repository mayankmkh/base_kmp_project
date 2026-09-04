package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import org.koin.core.scope.Scope

// The browser's Network tab is the request log on web, and the console belongs to the host page.
internal actual fun Scope.isDebugBuild(): Boolean = false

internal actual fun Scope.createPlatformContext(): PlatformContext = PlatformContext(ApplicationId)
