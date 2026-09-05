package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import dev.mayankmkh.basekmpproject.app.shared.config.KermitKtorLogger
import dev.mayankmkh.basekmpproject.app.shared.config.apiBaseUrl
import dev.mayankmkh.basekmpproject.capability.identity.impl.identityCapabilityModule
import dev.mayankmkh.basekmpproject.capability.posts.impl.postsCapabilityModule
import dev.mayankmkh.basekmpproject.capability.todos.impl.todosCapabilityModule
import dev.mayankmkh.basekmpproject.feature.posts.api.postsFeatureModule
import dev.mayankmkh.basekmpproject.feature.todos.api.todosFeatureModule
import dev.mayankmkh.basekmpproject.foundation.network.DynamicHeaders
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.createConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.shared
import dev.mayankmkh.basekmpproject.storage.database.AppDatabaseDriverProvider
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger as KtorLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.scope.Scope
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.includes
import org.koin.dsl.module
import org.koin.dsl.onClose

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
            ignoreUnknownKeys = true
            isLenient = false
            explicitNulls = false
            encodeDefaults = false
            coerceInputValues = true
        }
    }
}

private val dispatchersModule = module {
    single { AppDispatchers() }
}

internal val loggerModule = module {
    single { Logger(StaticConfig(logWriterList = listOf(platformLogWriter()))) }
}

private val runtimeModule = module {
    // Application ownership is explicit: capabilities take named children and close those children
    // with their Koin singleton; stopping Koin closes the parent as the final safety net.
    single {
        val logger = get<Logger>()
        val handler = CoroutineExceptionHandler { _, throwable ->
            logger.e(throwable) { "Uncaught application-runtime failure" }
        }
        ApplicationRuntimeScope(get<AppDispatchers>().cpu, handler)
    } onClose { it?.close() }
}

/** The platform handle shared by storage and platform modules. */
private val platformContextModule = module {
    single { createPlatformContext() }
}

/**
 * The one `HttpClient` the app talks to the network through.
 *
 * `single`, not `factory`: a client owns a connection pool and an engine, so handing every caller
 * its own would leak both. `NetworkConfig` is a definition of its own so a flavour or a test can
 * override just the host without rebuilding the plugin stack. The `CredentialProvider` comes from
 * `identityCapabilityModule` through App composition.
 */
private val networkModule = module {
    single {
        NetworkConfig(
            baseUrl = apiBaseUrl,
            // Headers, never bodies: the plugin buffers bodies to print them, and the sensitive
            // headers are sanitised inside the client. Release builds log nothing.
            logLevel = if (isDebugBuild()) LogLevel.HEADERS else LogLevel.NONE,
        )
    }
    // Locale comes from the app language owner and app version from platform build metadata once
    // either is required by the backend; the sample API needs no changing headers today.
    single<DynamicHeaders> { DynamicHeaders.None }
    singleOf(::KermitKtorLogger) bind KtorLogger::class
    single {
        createHttpClient(
            config = get(),
            credentialProvider = get(),
            headers = get(),
            clientLogger = get(),
            json = get(),
        )
    } onClose { it?.close() }
}

/**
 * Whether there is a network worth trying.
 *
 * `shared` turns the platform's cold flow into one registration in the application scope. The
 * `single` is still required so every Capability receives that one shared monitor object.
 */
private val connectivityModule = module {
    single<ConnectivityMonitor> {
        createConnectivityMonitor(get()).shared(get<ApplicationRuntimeScope>().scope)
    }
}

/**
 * The database is the source of truth the capabilities observe.
 *
 * The driver provider is a `single` because every Capability-generated database must use the same
 * open, migrated driver.
 */
private val databaseModule = module {
    singleOf(::AppDatabaseDriverProvider) bind SqlDriverProvider::class
}

// Declared last: top-level properties initialise in source order, so a list assembled any earlier
// would capture nulls.

internal val libModules =
    listOf(
        jsonModule,
        dispatchersModule,
        loggerModule,
        runtimeModule,
        platformContextModule,
        networkModule,
        connectivityModule,
        databaseModule,
    )

private val productModules =
    listOf(
        identityCapabilityModule,
        postsCapabilityModule,
        postsFeatureModule,
        todosCapabilityModule,
        todosFeatureModule,
    )

// One list so `KoinGraphTest` verifies the graph `initKoin` starts.
internal val appModules = libModules + productModules

/**
 * Whether this process is a debug build. Each target answers from its own runtime signal, so no
 * generated `BuildConfig` is needed in a library module.
 */
internal expect fun Scope.isDebugBuild(): Boolean

internal expect fun Scope.createPlatformContext(): PlatformContext
