package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.app.shared.config.ApplicationId
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.scope.Scope

// True for the framework's debug binary, which is what an Xcode Debug configuration links.
@OptIn(ExperimentalNativeApi::class)
internal actual fun Scope.isDebugBuild(): Boolean = Platform.isDebugBinary

internal actual fun Scope.createPlatformContext(): PlatformContext = PlatformContext(ApplicationId)
