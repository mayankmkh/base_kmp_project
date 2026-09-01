package dev.mayankmkh.basekmpproject.shared.app.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.shared.app.config.AppUseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.app.config.KermitKtorLogger
import dev.mayankmkh.basekmpproject.shared.app.config.NBRFailureListener
import dev.mayankmkh.basekmpproject.shared.features.details.di.detailsFeatureModule
import dev.mayankmkh.basekmpproject.shared.features.list.di.listFeatureModule
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.ConnectivityContext
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.createConnectivityMonitor
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.shared.libs.database.DatabaseContext
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsDatabaseProvider
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsDatabaseSource
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.AnonymousBearerTokenSource
import dev.mayankmkh.basekmpproject.shared.libs.networking.BearerTokenSource
import dev.mayankmkh.basekmpproject.shared.libs.networking.NetworkConfig
import dev.mayankmkh.basekmpproject.shared.libs.networking.createHttpClient
import dev.mayankmkh.basekmpproject.shared.libs.networking.prodBaseUrls
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import dev.mayankmkh.basekmpproject.shared.libs.prefs.CredentialsPreferences
import dev.mayankmkh.basekmpproject.shared.libs.prefs.PrefContext
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.http.Url
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
        modules(appModules)

        // Last, so what the caller declares wins: a later definition of the same type replaces the
        // one already loaded. That is how a test swaps the `HttpClient` for one on a `MockEngine`
        // without the app module knowing anything about tests.
        includes(config)
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

/**
 * Preferences, in the one shape this template still needs them.
 *
 * `CredentialsPreferences` is what makes the auth path in `:shared:libs:networking` a real one
 * rather than a sketch: swapping the `BearerTokenSource` binding below for a `single` that reads
 * from this store is the whole of adding sign-in, and nothing in the client changes. The explicit
 * `single` rather than `singleOf(::CredentialsPreferences)` is because the class has two one-arg
 * constructors -- the other takes a `DataStore` for tests -- and the reflective form cannot tell
 * which one is meant.
 */
private val prefsModule = module {
    factory { createPrefContext() }
    single { CredentialsPreferences(get<PrefContext>()) }
}

/**
 * The one `HttpClient` the app talks to the network through.
 *
 * `single`, not `factory`: a client owns a connection pool and an engine, so handing every caller
 * its own would leak both. `NetworkConfig` is a definition of its own so a flavour or a test can
 * override just the host without rebuilding the plugin stack.
 */
private val networkModule = module {
    single { NetworkConfig(baseUrl = Url(prodBaseUrls.main), defaultHeaders = emptyMap()) }
    single<BearerTokenSource> { AnonymousBearerTokenSource }
    singleOf(::KermitKtorLogger) bind KtorLogger::class
    single {
        createHttpClient(
            config = get(),
            bearerTokenSource = get(),
            clientLogger = get(),
            json = get(),
        )
    }
}

/**
 * Whether there is a network worth trying.
 *
 * `single`: on Android and iOS the monitor registers a system callback, and one registration shared
 * by every collector is the point -- a `factory` would open a fresh one per use case and leak the
 * lot. The context is a `factory` for the same reason the other platform contexts are: it is a thin
 * wrapper the `single` below consumes once.
 */
private val connectivityModule = module {
    factory { createConnectivityContext() }
    single<ConnectivityMonitor> { createConnectivityMonitor(get()) }
}

/**
 * The cache the repositories treat as their source of truth.
 *
 * The provider is a `single` because it memoises the open database; a second instance would open a
 * second connection to the same file and the two would not see each other's writes.
 */
private val databaseModule = module {
    factory { createDatabaseContext() }
    singleOf(::PostsDatabaseProvider) bind PostsDatabaseSource::class
    singleOf(::PostsLocalStore)
}

private val postsModule = module {
    singleOf(::PostsApi)
}

// Declared last: top-level properties initialise in source order, so a list assembled any earlier
// would capture nulls.

private val libModules =
    listOf(
        jsonModule,
        dispatchersModule,
        loggerModule,
        archModule,
        prefsModule,
        networkModule,
        connectivityModule,
        databaseModule,
        postsModule,
    )

private val featureModules = listOf(listFeatureModule, detailsFeatureModule)

// One list so `KoinGraphTest` verifies the graph `initKoin` starts.
internal val appModules = libModules + featureModules

internal expect fun Scope.createPrefContext(): PrefContext

internal expect fun Scope.createDatabaseContext(): DatabaseContext

internal expect fun Scope.createConnectivityContext(): ConnectivityContext
