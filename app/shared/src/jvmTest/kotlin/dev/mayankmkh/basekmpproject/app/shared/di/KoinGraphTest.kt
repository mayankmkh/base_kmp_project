package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.LoggerConfig
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStore
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinGraphTest {

    @Test
    fun `every declared dependency resolves`() {
        // One including module rather than `List<Module>.verifyAll`, which verifies each module on
        // its own: capability and feature modules depend on bindings the app modules declare, so
        // isolated
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
                CoroutineExceptionHandler::class,
                FeatureInstanceKey::class,
                PostId::class,
                // Opened inside the Identity module's `CredentialStore` definition lambda.
                PreferenceStore::class,
                LoggerConfig::class,
                // `NetworkConfig`'s own fields, filled in by the app environment rather than Koin.
                Url::class,
                Duration::class,
                // `HttpClient`'s constructor: the engine is resolved off the classpath by
                // `HttpClient { }` and the config block is the lambda itself.
                HttpClientEngine::class,
                HttpClientConfig::class,
            )
    }
}
