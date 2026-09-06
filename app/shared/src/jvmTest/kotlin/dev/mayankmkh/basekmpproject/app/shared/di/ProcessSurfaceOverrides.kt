package dev.mayankmkh.basekmpproject.app.shared.di

import dev.mayankmkh.basekmpproject.foundation.preferences.PreferenceStores
import dev.mayankmkh.basekmpproject.foundation.preferences.inMemoryPreferenceStores
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The surfaces a test swaps and nothing else: the two stored-data factories, which production code
 * may build at most once per process, and the HTTP engine, which would otherwise reach the network.
 * Everything else stays the graph `initKoin` starts.
 *
 * A null [engine] leaves the platform engine definition in place, for the one test that has to
 * watch the real engine close.
 */
internal fun processSurfaceOverrides(engine: HttpClientEngine?): Module = module {
    single<PreferenceStores> { inMemoryPreferenceStores() }
    single<SecretStores> { inMemorySecretStores() }
    if (engine != null) {
        single<HttpClientEngine> { engine }
    }
}
