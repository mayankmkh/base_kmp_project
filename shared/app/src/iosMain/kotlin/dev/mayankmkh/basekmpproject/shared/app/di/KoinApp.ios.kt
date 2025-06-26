package dev.mayankmkh.basekmpproject.shared.app.di

import dev.mayankmkh.basekmpproject.shared.libs.prefs.PrefContext
import org.koin.core.scope.Scope

internal actual fun Scope.createPrefContext(): PrefContext = PrefContext()
