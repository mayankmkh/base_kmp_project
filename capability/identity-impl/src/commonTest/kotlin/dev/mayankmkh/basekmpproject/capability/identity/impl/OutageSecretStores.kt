package dev.mayankmkh.basekmpproject.capability.identity.impl

import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStore
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStoreException
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Secret stores whose every operation fails with [SecretStoreException] while [down], as a platform
 * store does when its keyset or Keychain cannot be read, and that keep their contents for when it
 * comes back. Each collection of `observe` re-checks, as a real store re-reads.
 */
internal class OutageSecretStores : SecretStores {
    private val real = inMemorySecretStores()
    var down = false
    var failedObservations = 0
        private set

    override fun open(name: String): SecretStore = OutageStore(real.open(name))

    private inner class OutageStore(private val real: SecretStore) : SecretStore {
        override suspend fun get(key: String): String? = available { real.get(key) }

        override fun observe(key: String): Flow<String?> = flow {
            if (down) {
                failedObservations++
                throw SecretStoreException("the store is unreadable")
            }
            emitAll(real.observe(key))
        }

        override suspend fun set(key: String, value: String) = available { real.set(key, value) }

        override suspend fun remove(key: String) = available { real.remove(key) }

        override suspend fun clear() = available { real.clear() }

        private inline fun <T> available(block: () -> T): T =
            if (down) throw SecretStoreException("the store is unreadable") else block()
    }
}
