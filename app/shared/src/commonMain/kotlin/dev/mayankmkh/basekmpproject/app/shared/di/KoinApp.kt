package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.koin.KermitKoinLogger
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
import dev.mayankmkh.basekmpproject.foundation.network.createJson
import dev.mayankmkh.basekmpproject.foundation.network.createPlatformHttpClientEngine
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import dev.mayankmkh.basekmpproject.foundation.preferences.preferenceStores
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.createConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.connectivity.shared
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.secretStores
import dev.mayankmkh.basekmpproject.storage.database.AppDatabaseDriverProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger as KtorLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.scope.Scope
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.includes
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.mp.KoinPlatform
import org.koin.plugin.module.dsl.single

/**
 * Starts the application graph.
 *
 * The module list is written out one literal name per line and nothing else. A call, a spread, a
 * variable or an inline `module { }` here would be opaque to the Koin compiler plugin: it reports
 * `KOIN-W003` and silently stops checking typed definitions across the whole graph.
 * `KoinApplicationModulesRuleTest` holds that shape.
 *
 * [isDebug] is the entry point's own build signal and the one runtime value the graph cannot
 * compute for itself, so it enters as a Koin property rather than as a definition declared here.
 * Production PreferenceStores and SecretStores may be built at most once per process, so tests
 * replace those factories through [config].
 */
fun initKoin(isDebug: Boolean, config: KoinAppDeclaration? = null): KoinApplication = startKoin {
    properties(mapOf(IsDebugProperty to isDebug))
    modules(
        environmentModule,
        jsonModule,
        dispatchersModule,
        runtimeModule,
        platformContextModule,
        storesModule,
        networkModule,
        connectivityModule,
        databaseModule,
        identityCapabilityModule,
        postsCapabilityModule,
        postsFeatureModule,
        todosCapabilityModule,
        todosFeatureModule,
    )

    // Last, so what the caller declares wins: a later definition of the same type replaces the
    // one already loaded. That is how a test swaps platform resource factories without the app
    // module knowing anything about tests.
    includes(config)

    // Koin's own logger is the one consumer that cannot be served from the graph before the graph
    // exists, so it is installed one step later rather than from a second [AppEnvironment] built
    // here. The process keeps one environment and one app [Logger]; the price is Koin's own
    // "loaded N definitions" line, which is emitted while its logger is still the empty default.
    val environment = koin.get<AppEnvironment>()
    logger(
        KermitKoinLogger(environment.logger.withTag("koin")).apply { level = environment.koinLevel }
    )
}

/** The build signal, carried as a property because a definition here would be a dynamic module. */
internal const val IsDebugProperty: String = "app.isDebug"

/** Cancels application work before Koin releases resources in unspecified callback order. */
fun shutdownKoin() {
    KoinPlatform.getKoinOrNull()?.getOrNull<ApplicationRuntimeScope>()?.close()
    stopKoin()
}

/** The one place the build type turns into log verbosity; every gate below reads from here. */
internal class AppEnvironment(val isDebug: Boolean) {
    val minSeverity: Severity = if (isDebug) Severity.Verbose else Severity.Warn
    val koinLevel: Level = if (isDebug) Level.DEBUG else Level.WARNING
    // Headers, never bodies: the plugin buffers bodies to print them, and the sensitive headers are
    // sanitised inside the client. Release builds log nothing.
    val ktorLogLevel: LogLevel = if (isDebug) LogLevel.HEADERS else LogLevel.NONE
    val logger: Logger =
        Logger(
            StaticConfig(minSeverity = minSeverity, logWriterList = listOf(appLogWriter(isDebug)))
        )
}

private val jsonModule = module {
    single { createJson() }
}

private val dispatchersModule = module {
    single { AppDispatchers() }
}

private val environmentModule = module {
    single { AppEnvironment(getProperty(IsDebugProperty)) }
    single { get<AppEnvironment>().logger }
}

private val runtimeModule = module {
    // Application ownership is explicit: capabilities take named children and close those children
    // with their Koin singleton; stopping Koin closes the parent as the final safety net.
    single {
        val logger = get<Logger>().withTag("runtime")
        val handler = CoroutineExceptionHandler { _, throwable ->
            logger.e(throwable) { "Uncaught application-runtime failure" }
        }
        ApplicationRuntimeScope(get<AppDispatchers>().cpu, handler)
    } onClose { it?.close() }
}

/** The platform handle shared by storage and platform modules. */
private val platformContextModule = module {
    // startKoin applies includes(config) before eager creation, so Android's Context is present.
    single(createdAtStart = true) { createPlatformContext() }
}

/** The factories every stored file is opened through; Capability implementations take them. */
private val storesModule = module {
    single<PreferenceStores> { preferenceStores(get(), get()) }
    single<SecretStores> { secretStores(get(), get()) }
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
    single { NetworkConfig(baseUrl = apiBaseUrl, logLevel = get<AppEnvironment>().ktorLogLevel) }
    // Locale comes from the app language owner and app version from platform build metadata once
    // either is required by the backend; the sample API needs no changing headers today.
    single<DynamicHeaders> { DynamicHeaders.None }
    single<KtorLogger> { KermitKtorLogger(get()) }
    // A client built over a supplied engine does not own it, so the engine closes with the graph.
    single<HttpClientEngine> { createPlatformHttpClientEngine() } onClose { it?.close() }
    single {
        createHttpClient(
            engine = get(),
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
    // onClose before bind: after bind the definition is typed by the contract, which has no close.
    single<AppDatabaseDriverProvider>() onClose
        {
            it?.close()
        } bind
        SqlDriverProvider::class
}

/**
 * Where log lines go on this target. Kermit's `platformLogWriter()` is tuned for local development,
 * so a target may pick a different writer for release builds.
 */
internal expect fun appLogWriter(isDebug: Boolean): LogWriter

internal expect fun Scope.createPlatformContext(): PlatformContext
