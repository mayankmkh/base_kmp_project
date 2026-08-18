package dev.mayankmkh.basekmpproject.shared.app.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.shared.app.config.AppUseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.app.config.NBRFailureListener
import dev.mayankmkh.basekmpproject.shared.features.details.di.detailsFeatureModule
import dev.mayankmkh.basekmpproject.shared.features.list.di.listFeatureModule
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.shared.libs.prefs.KeyValueStore
import dev.mayankmkh.basekmpproject.shared.libs.prefs.PrefContext
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.scope.Scope
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.includes
import org.koin.dsl.module

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        includes(config)

        modules(appModules)
    }
}

private val jsonModule = module {
    single {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    }
}

private val dispatchersModule = module { single { AppDispatchers() } }

internal val loggerModule = module {
    single { Logger(StaticConfig(logWriterList = listOf(platformLogWriter()))) }
}

private val archModule = module {
    singleOf(::NBRFailureListener) bind NetworkBoundResource.OnFailureListener::class
    singleOf(::AppUseCaseFailureListener) bind UseCaseFailureListener::class
}

private val prefsModule = module {
    includes(jsonModule)
    factory { createPrefContext() }
    singleOf(::KeyValueStore)
}

// Declared last: top-level properties initialise in source order, so a list assembled any earlier
// would capture nulls.

private val libModules =
    listOf(jsonModule, dispatchersModule, loggerModule, archModule, prefsModule)

private val featureModules = listOf(listFeatureModule, detailsFeatureModule)

// One list so `KoinGraphTest` verifies the graph `initKoin` starts.
internal val appModules = libModules + featureModules

internal expect fun Scope.createPrefContext(): PrefContext
