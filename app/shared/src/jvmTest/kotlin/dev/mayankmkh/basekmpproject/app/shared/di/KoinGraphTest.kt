package dev.mayankmkh.basekmpproject.app.shared.di

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.LoggerConfig
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import dev.mayankmkh.basekmpproject.capability.identity.api.AuthToken
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityCommands
import dev.mayankmkh.basekmpproject.capability.identity.api.IdentityQueries
import dev.mayankmkh.basekmpproject.capability.identity.impl.identityCapabilityModule
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.capability.posts.impl.postsCapabilityModule
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosCommands
import dev.mayankmkh.basekmpproject.capability.todos.api.TodosQueries
import dev.mayankmkh.basekmpproject.capability.todos.impl.todosCapabilityModule
import dev.mayankmkh.basekmpproject.feature.posts.api.postsFeatureModule
import dev.mayankmkh.basekmpproject.feature.todos.api.todosFeatureModule
import dev.mayankmkh.basekmpproject.foundation.network.CredentialProvider
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.sqldelight.SqlDriverProvider
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.storage.database.AppDatabaseDriverProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.http.Url
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
import org.koin.core.Koin
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.indexKey
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * The graph `initKoin` starts, checked at runtime.
 *
 * Every test here starts the real entry point rather than assembling a module list of its own: the
 * list is written out literally inside `initKoin` so the Koin compiler plugin can read it, and a
 * second copy in test code would be the thing that drifts.
 */
class KoinGraphTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every declared dependency resolves`() {
        // Compile-time validation covers typed definitions graph-wide, and the root-resolution
        // test below runs the classic definition bodies. Verification is what still reaches the
        // constructors behind a lambda definition, ViewModels with runtime parameters included.
        val application = initKoin(isDebug = true, environment = BuildEnvironment.Staging)
        try {
            loadedDefinitions(application.koin)
                .verify(
                    extraTypes = LAMBDA_CONSTRUCTOR_ARGUMENTS,
                    injections = VIEW_MODEL_PARAMETERS,
                )
        } finally {
            shutdownKoin()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `every application root resolves through definition bodies`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val application =
            initKoin(isDebug = true, environment = BuildEnvironment.Staging) {
                modules(processSurfaceOverrides(MockEngine { respondOk() }))
            }
        try {
            with(application.koin) {
                get<PostsQueries>()
                get<PostsCommands>()
                get<TodosQueries>()
                get<TodosCommands>()
                get<IdentityQueries>()
                // The provider is its own object, so the client never depends on Identity; what
                // the two share is the one credential store.
                val commands = get<IdentityCommands>()
                val provider = get<CredentialProvider>()
                runTest {
                    commands.signIn(AuthToken("graph-test-token"))
                    assertEquals("graph-test-token", provider.currentCredential())
                    commands.signOut()
                    assertNull(provider.currentCredential())
                }
                get<ConnectivityMonitor>()
                get<SqlDriverProvider>()
                get<PreferenceStores>()
                get<SecretStores>()
                get<HttpClient>()
                get<ApplicationRuntimeScope>()
                get<PlatformContext>()
            }
        } finally {
            shutdownKoin()
            Dispatchers.resetMain()
        }
    }

    /**
     * Every definition the capability and feature modules declare is the one the started root
     * holds, and every non-ViewModel body runs. A module missing from `initKoin`, or a later
     * definition shadowing one of these, fails here by name rather than at the first call that
     * reaches the wrong instance. ViewModels are left to `every declared dependency resolves`: two
     * take their route's id as a parameter, and resolving one outside a ViewModel store would leak
     * it.
     */
    @OptIn(ExperimentalCoroutinesApi::class, KoinInternalApi::class)
    @Test
    fun `every capability and feature definition is the one the app root holds`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val application =
            initKoin(isDebug = true, environment = BuildEnvironment.Staging) {
                modules(processSurfaceOverrides(MockEngine { respondOk() }))
            }
        try {
            val koin = application.koin
            val loaded = koin.instanceRegistry.instances
            CAPABILITY_AND_FEATURE_MODULES.forEach { (name, module) ->
                // The Koin compiler plugin binds every typed `viewModel<T>()` to `ViewModel` as
                // well, so that one key is shared by design and the last module loaded holds it.
                val keys =
                    module.mappings
                        .filterNot { (key, factory) ->
                            val definition = factory.beanDefinition
                            key ==
                                indexKey(
                                    ViewModel::class,
                                    definition.qualifier,
                                    definition.scopeQualifier,
                                )
                        }
                        .keys
                assertTrue(keys.isNotEmpty(), "$name declares nothing")
                val shadowed = keys.filterNot { loaded[it] === module.mappings[it] }
                assertEquals(emptyList(), shadowed, "$name is not what the app root holds")
                module.mappings.values
                    .map { it.beanDefinition }
                    .distinct()
                    .filterNot { ViewModel::class.java.isAssignableFrom(it.primaryType.java) }
                    .forEach { definition ->
                        koin.get<Any>(definition.primaryType, definition.qualifier)
                    }
            }
        } finally {
            shutdownKoin()
            Dispatchers.resetMain()
        }
    }

    /**
     * Koin indexes a definition by [org.koin.core.definition.indexKey], and on Kotlin/Wasm and
     * Kotlin/JS `KClass.getFullName()` is `simpleName` -- the package is gone. Two graph types
     * sharing a simple name therefore share one index key on those targets: the module loaded later
     * silently overrides the earlier one, and a definition whose body injects the shadowed type
     * resolves back into itself and recurses until the stack dies. The JVM keys by the qualified
     * name and never sees it, so this test keys the started graph the way the web build does.
     */
    @OptIn(ExperimentalCoroutinesApi::class, KoinInternalApi::class)
    @Test
    fun `no two graph types share a simple name`() {
        // `shutdownKoin` resolves the application runtime scope, which needs a main dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val application = initKoin(isDebug = true, environment = BuildEnvironment.Staging)
        val typesByWebIndexKey = mutableMapOf<String, MutableSet<KClass<*>>>()
        try {
            application.koin.instanceRegistry.instances.values
                .map { it.beanDefinition }
                .forEach { definition ->
                    val indexedTypes = listOf(definition.primaryType) + definition.secondaryTypes
                    indexedTypes.forEach { type ->
                        val key =
                            listOf(
                                    type.simpleName ?: type.toString(),
                                    definition.qualifier?.value.orEmpty(),
                                    definition.scopeQualifier.toString(),
                                )
                                .joinToString(":")
                        typesByWebIndexKey.getOrPut(key) { mutableSetOf() }.add(type)
                    }
                }
        } finally {
            shutdownKoin()
            Dispatchers.resetMain()
        }

        val collisions = typesByWebIndexKey.filterValues { it.size > 1 }
        assertTrue(
            collisions.isEmpty(),
            "These types collapse onto one Koin index key on web targets. Give one of each pair a " +
                "qualifier, or stop declaring it as a definition:\n" +
                collisions.entries.joinToString("\n") { (key, types) ->
                    "\t'$key' <- " + types.joinToString(", ") { it.qualifiedName ?: it.toString() }
                },
        )
    }

    @OptIn(KoinInternalApi::class)
    @Test
    fun `closing koin closes the http client, its engine and the sql driver`() = runTest {
        // The real HTTP engine, so closing it is what the assertions watch; the stored-data
        // factories stay in memory and the database in a temporary file, so the graph opens
        // nothing of the developer's on any operating system.
        val application =
            initKoin(isDebug = true, environment = BuildEnvironment.Staging) {
                modules(processSurfaceOverrides(engine = null))
            }
        try {
            val client = application.koin.get<HttpClient>()
            val engine = application.koin.get<HttpClientEngine>()
            val driverProvider = application.koin.get<SqlDriverProvider>()
            val database = assertIs<TemporaryDatabaseDriverProvider>(driverProvider).file
            val temporaryDirectory = File(System.getProperty("java.io.tmpdir")).canonicalFile
            val driver = driverProvider.driver()
            driver.execute(null, "SELECT 1", 0).value
            assertTrue(
                database.isFile && database.canonicalFile.startsWith(temporaryDirectory),
                "The test database is at $database, outside $temporaryDirectory",
            )
            assertTrue(client.isActive)
            // The override above swaps the contract binding for a temporary file, so what closes
            // here is the test double. The production definition stays registered under its own
            // type; never resolved, it still has to carry the close the real database relies on.
            val production =
                application.koin.instanceRegistry.instances.values
                    .map { it.beanDefinition }
                    .distinct()
                    .single { it.primaryType == AppDatabaseDriverProvider::class }
            assertTrue(
                production.callbacks.onClose != null,
                "The production AppDatabaseDriverProvider definition lost its onClose",
            )

            shutdownKoin()

            assertFalse(client.isActive)
            assertFalse(engine.isActive)
            assertFails { driverProvider.driver() }
            assertFalse(database.exists(), "The test database outlived the graph at $database")
        } finally {
            shutdownKoin()
        }
    }

    /**
     * One module holding every factory the running instance loaded. Verification reads a module's
     * mappings, and this is the only way to hand it the started graph without naming the modules a
     * second time.
     */
    @OptIn(KoinInternalApi::class)
    private fun loadedDefinitions(koin: Koin): Module =
        module {}.apply { mappings.putAll(koin.instanceRegistry.instances) }

    private companion object {
        // Verification reflects over each bound type's constructor and never runs a definition
        // lambda. These are constructor arguments the lambda itself fills in, not parameters that
        // a caller supplies later through parametersOf.
        val LAMBDA_CONSTRUCTOR_ARGUMENTS =
            listOf(
                CoroutineDispatcher::class,
                CoroutineExceptionHandler::class,
                LoggerConfig::class,
                // The build signals reach `AppEnvironment` as Koin properties, not definitions.
                Boolean::class,
                BuildEnvironment::class,
                // `NetworkConfig` fields are filled from the app environment and defaults.
                Url::class,
                Duration::class,
                LogLevel::class,
                // The `HttpClient` config block is the second constructor argument.
                HttpClientConfig::class,
            )

        // Written out rather than read from `initKoin`, which keeps its list literal for the Koin
        // compiler plugin; `KoinApplicationModulesRuleTest` holds that list.
        val CAPABILITY_AND_FEATURE_MODULES =
            listOf(
                "identityCapabilityModule" to identityCapabilityModule,
                "postsCapabilityModule" to postsCapabilityModule,
                "postsFeatureModule" to postsFeatureModule,
                "todosCapabilityModule" to todosCapabilityModule,
                "todosFeatureModule" to todosFeatureModule,
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
