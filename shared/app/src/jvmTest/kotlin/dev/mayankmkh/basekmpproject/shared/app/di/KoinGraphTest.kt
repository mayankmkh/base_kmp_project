package dev.mayankmkh.basekmpproject.shared.app.di

import co.touchlab.kermit.LoggerConfig
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinGraphTest {

    @Test
    fun `every declared dependency resolves`() {
        // One including module rather than `List<Module>.verifyAll`, which verifies each module on
        // its own: the feature modules depend on bindings the app modules declare, so isolated
        // verification would report holes the running app does not have.
        module { includes(appModules) }.verify(extraTypes = LAMBDA_BUILT)
    }

    private companion object {
        // Verification reflects over the public constructors of every declared type, blind to the
        // definition body. These types are built by hand inside a `single { }` lambda, so their own
        // constructor parameters never come from the graph and would read as missing bindings.
        val LAMBDA_BUILT =
            listOf(
                CoroutineDispatcher::class,
                LoggerConfig::class,
                // `NetworkConfig`'s own fields, filled in from `BaseUrls` rather than the graph.
                Url::class,
                ContentType::class,
                Map::class,
                // `HttpClient`'s constructor: the engine is resolved off the classpath by
                // `HttpClient { }` and the config block is the lambda itself.
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
    }
}
