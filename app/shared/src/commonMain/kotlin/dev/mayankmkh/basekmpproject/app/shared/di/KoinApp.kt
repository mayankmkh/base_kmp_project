package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.koin.KermitKoinLogger
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import dev.mayankmkh.basekmpproject.app.shared.config.KermitKtorLogger
import dev.mayankmkh.basekmpproject.app.shared.config.apiBaseUrl
import dev.mayankmkh.basekmpproject.app.shared.config.applicationId
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
import io.ktor.http.Url
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
 * [isDebug] and [environment] are the entry point's own build signals and the two runtime values
 * the graph cannot compute for itself, so they enter as Koin properties rather than as definitions
 * declared here. Production PreferenceStores and SecretStores may be built at most once per
 * process, so tests replace those factories through [config].
 */
fun initKoin(
    isDebug: Boolean,
    environment: BuildEnvironment,
    config: KoinAppDeclaration? = null,
): KoinApplication = startKoin {
    properties(mapOf(IsDebugProperty to isDebug, EnvironmentProperty to environment))
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
    val appEnvironment = koin.get<AppEnvironment>()
    logger(
        KermitKoinLogger(appEnvironment.logger.withTag("koin")).apply {
            level = appEnvironment.koinLevel
        }
    )
}

/** The build signals, carried as properties because a definition here would be a dynamic module. */
internal const val IsDebugProperty: String = "app.isDebug"

internal const val EnvironmentProperty: String = "app.environment"

/** Cancels application work before Koin releases resources in unspecified callback order. */
fun shutdownKoin() {
    KoinPlatform.getKoinOrNull()?.getOrNull<ApplicationRuntimeScope>()?.close()
    stopKoin()
}

/**
 * The one place the two build signals turn into configuration; every gate below reads from here.
 *
 * [isDebug] answers "was this built for development", [environment] answers "which deployment does
 * it talk to". They are independent, and a value derived here says which of the two it depends on.
 */
internal class AppEnvironment(val isDebug: Boolean, val environment: BuildEnvironment) {
    /** Storage identity, so the two environments never share a database, a store or a secret. */
    val applicationId: String = environment.applicationId
    val apiBaseUrl: Url = environment.apiBaseUrl
    // Two gates in series, and Kermit's is the outer one: it drops anything below [minSeverity]
    // no matter what a plugin was told to emit. So a level here that admits HTTP lines is what
    // makes [ktorLogLevel] mean anything at all -- `AppEnvironmentTest` holds the two together.
    val minSeverity: Severity =
        when {
            isDebug -> Severity.Verbose
            // A staging release exists to be diagnosed, and `KermitKtorLogger` emits at Debug.
            environment == BuildEnvironment.Staging -> Severity.Debug
            else -> Severity.Warn
        }
    val koinLevel: Level = if (isDebug) Level.DEBUG else Level.WARNING
    // Never bodies at any level: the plugin has to buffer a body to print it.
    val ktorLogLevel: LogLevel =
        when {
            // A developer's own machine. Headers are worth having here, and the credential-bearing
            // ones are sanitised inside the client.
            isDebug -> LogLevel.HEADERS
            // A shipped artifact, so the call timeline and no headers at all. This costs no failure
            // diagnostics: `NetworkFailure` carries its own request id and response body, seeded
            // before the first attempt, so what you debug from survives independently of this gate.
            // Headers would only add the ones on calls that succeeded, in a build a tester is
            // holding.
            environment == BuildEnvironment.Staging -> LogLevel.INFO
            else -> LogLevel.NONE
        }
    val logger: Logger =
        Logger(
            StaticConfig(
                minSeverity = minSeverity,
                logWriterList = listOf(appLogWriter(isDebug, applicationId)),
            )
        )
}

private val jsonModule = module {
    single { createJson() }
}

private val dispatchersModule = module {
    single { AppDispatchers() }
}

private val environmentModule = module {
    single { AppEnvironment(getProperty(IsDebugProperty), getProperty(EnvironmentProperty)) }
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
    single {
        val environment = get<AppEnvironment>()
        NetworkConfig(baseUrl = environment.apiBaseUrl, logLevel = environment.ktorLogLevel)
    }
    // Locale comes from the app language owner and app version from platform build metadata once
    // either is required by the backend; the sample API needs no changing headers today.
    single<DynamicHeaders> { DynamicHeaders.None }
    // A client built over a supplied engine does not own it, so the engine closes with the graph.
    single<HttpClientEngine> { createPlatformHttpClientEngine() } onClose { it?.close() }
    single {
        createHttpClient(
            engine = get(),
            config = get(),
            credentialProvider = get(),
            headers = get(),
            // Constructed here rather than declared as a `single<KtorLogger>`. Ktor's `Logger` and
            // Kermit's share a simple name, and web targets index a definition by simple name
            // alone: two `Logger` definitions become one index key there, the later one wins, and
            // this body's `get<Logger>()` resolves back into itself until the stack dies. The
            // client is the only consumer, so the adapter has no reason to be in the graph.
            // `KoinGraphTest` holds the no-collision rule for every other type.
            clientLogger = KermitKtorLogger(get()),
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
internal expect fun appLogWriter(isDebug: Boolean, applicationId: String): LogWriter

internal expect fun Scope.createPlatformContext(): PlatformContext
