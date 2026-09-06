package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LoggerConfig
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.foundation.network.AnonymousCredentialProvider
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import dev.mayankmkh.basekmpproject.foundation.preferences.inMemoryPreferenceStores
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.http.Url
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class KoinGraphTest {
    // The graph is the same either way; a debug environment keeps the logger's own configuration
    // under test rather than a filtered one.
    private val environment = AppEnvironment(isDebug = true)

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every declared dependency resolves`() {
        // Compile-time validation checks typed wiring, while verify() keeps runtime graph coverage
        // and the root-resolution test runs definition bodies.
        // One including module rather than `List<Module>.verifyAll`, which verifies each module on
        // its own: capability and feature modules depend on bindings the app modules declare, so
        // isolated verification would report holes the running app does not have.
        module { includes(appModules(environment)) }
            .verify(extraTypes = LAMBDA_CONSTRUCTOR_ARGUMENTS, injections = VIEW_MODEL_PARAMETERS)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `every application root resolves through definition bodies`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val application = koinApplication {
                modules(
                    appModules(environment) +
                        module {
                            single<PreferenceStores> { inMemoryPreferenceStores() }
                            single<SecretStores> { inMemorySecretStores() }
                            single<HttpClientEngine> { MockEngine { respondOk() } }
                        }
                )
            }
            try {
                with(application.koin) {
                    get<PostsQueries>()
                    get<PostsCommands>()
                    get<TodosQueries>()
                    get<TodosCommands>()
                    val identity = get<IdentityQueries>()
                    assertSame<Any>(identity, get<CredentialProvider>())
                    get<ConnectivityMonitor>()
                    get<SqlDriverProvider>()
                    get<PreferenceStores>()
                    get<SecretStores>()
                    get<HttpClient>()
                    get<ApplicationRuntimeScope>()
                    get<PlatformContext>()
                }
            } finally {
                application.close()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `closing koin closes the http client, its engine and the sql driver`() = runTest {
        // The library modules alone, with an anonymous provider in place of the Identity module,
        // so resolving the client opens no secure storage on the test machine.
        val originalUserHome = System.getProperty("user.home")
        val testUserHome = Files.createTempDirectory("base-kmp-koin-close-test")
        System.setProperty("user.home", testUserHome.toString())
        val application = koinApplication {
            modules(
                libModules(environment) +
                    module { single<CredentialProvider> { AnonymousCredentialProvider } }
            )
        }
        try {
            val client = application.koin.get<HttpClient>()
            val engine = application.koin.get<HttpClientEngine>()
            val driverProvider = application.koin.get<SqlDriverProvider>()
            val driver = driverProvider.driver()
            driver.execute(null, "SELECT 1", 0).value
            assertTrue(client.isActive)

            application.close()

            assertFalse(client.isActive)
            assertFalse(engine.isActive)
            assertFails { driverProvider.driver() }
        } finally {
            application.close()
            System.setProperty("user.home", originalUserHome)
            testUserHome.toFile().deleteRecursively()
        }
    }

    private companion object {
        // Verification reflects over each bound type's constructor and never runs a definition
        // lambda. These are constructor arguments the lambda itself fills in, not parameters that
        // a caller supplies later through parametersOf.
        val LAMBDA_CONSTRUCTOR_ARGUMENTS =
            listOf(
                CoroutineDispatcher::class,
                CoroutineExceptionHandler::class,
                LoggerConfig::class,
                // `NetworkConfig` fields are filled from the app environment and defaults.
                Url::class,
                Duration::class,
                LogLevel::class,
                // The `HttpClient` config block is the second constructor argument.
                HttpClientConfig::class,
            )

        // Koin's public `definition<T>()` helper cannot name internal ViewModel classes from this
        // app module. Resolve those KClasses by name so each runtime parameter remains attached to
        // the concrete definition that consumes it rather than becoming a graph-wide allow-list.
        @OptIn(KoinExperimentalAPI::class)
        val VIEW_MODEL_PARAMETERS =
            injectedParameters(
                // helix:generated:view-model-parameters BEGIN
                viewModelParameters(
                    "dev.mayankmkh.basekmpproject.feature.posts.PostDetailViewModel",
                    "dev.mayankmkh.basekmpproject.capability.posts.api.PostId",
                ),
                viewModelParameters(
                    "dev.mayankmkh.basekmpproject.feature.todos.TodoDetailViewModel",
                    "dev.mayankmkh.basekmpproject.capability.todos.api.TodoId",
                ),
                // helix:generated:view-model-parameters END
            )

        @OptIn(KoinExperimentalAPI::class)
        fun viewModelParameters(
            className: String,
            vararg parameterClassNames: String,
        ): ParameterTypeInjection =
            ParameterTypeInjection(
                targetType = Class.forName(className).kotlin,
                injectedTypes = parameterClassNames.map { Class.forName(it).kotlin },
            )
    }
}
