package dev.mayankmkh.basekmpproject.platform.securestorage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** For tests, the in-memory factory and targets that reject persistent page secrets. */
internal fun inMemorySecretStore(): SecretStore = MemorySecretStore()

/**
 * Also the snapshot behind the iOS Keychain actual, which writes through to the Keychain and then
 * to this; the Keychain has no change notifications, so `observe` is fed from here.
 */
internal class MemorySecretStore : SecretStore {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Replaces every value in one emission, for loading a persisted snapshot. */
    fun replaceAll(snapshot: Map<String, String>) {
        values.value = snapshot
    }

    override suspend fun get(key: String): String? = values.value[key]

    override fun observe(key: String): Flow<String?> = values.map { it[key] }.distinctUntilChanged()

    override suspend fun set(key: String, value: String) {
        values.update { it + (key to value) }
    }

    override suspend fun remove(key: String) {
        values.update { it - key }
    }

    override suspend fun clear() {
        values.value = emptyMap()
    }
}
