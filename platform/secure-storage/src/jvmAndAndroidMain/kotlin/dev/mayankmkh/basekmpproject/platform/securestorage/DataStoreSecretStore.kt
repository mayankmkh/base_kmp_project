package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.causeClassName
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The store Android and desktop share: a typed DataStore of `Map<String, String>` in one file.
 *
 * Both [produceSerializer] and [produceFile] run on the DataStore scope at the first read or write,
 * not when the store opens. That keeps the Android Keystore or desktop vault lookup, the keyset
 * read and the directory creation off the thread that resolves the Koin graph.
 */
internal fun dataStoreSecretStore(
    name: String,
    logger: Logger,
    produceSerializer: () -> Serializer<Map<String, String>>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): SecretStore =
    DataStoreSecretStore(
        DataStoreFactory.create(
            serializer = LazySerializer(produceSerializer),
            // A file that cannot be decrypted or parsed is dropped rather than rethrown at every
            // read; on this store that signs the user out, so it is worth a line. The failure class
            // separates a lost keyset from malformed JSON; neither the file nor its keys are
            // logged.
            corruptionHandler =
                ReplaceFileCorruptionHandler { failure ->
                    logger.w {
                        logEvent(
                            "secret_store_replaced",
                            "name" to name,
                            "causeClass" to failure.causeClassName,
                        )
                    }
                    emptyMap()
                },
            scope = scope,
            produceFile = { produceFile().also { it.parentFile?.ensureDirectory() } },
        )
    )

// DataStore reports storage failures as `IOException`; the contract on `SecretStore` is
// `SecretStoreException` on every platform, so the wrapping happens once here.
private class DataStoreSecretStore(private val dataStore: DataStore<Map<String, String>>) :
    SecretStore {
    override suspend fun get(key: String): String? = storage { dataStore.data.first()[key] }

    override fun observe(key: String): Flow<String?> =
        dataStore.data
            .map { it[key] }
            .distinctUntilChanged()
            .catch { failure -> throw failure.asSecretStoreException() }

    override suspend fun set(key: String, value: String) {
        storage { dataStore.updateData { it + (key to value) } }
    }

    override suspend fun remove(key: String) {
        storage { dataStore.updateData { it - key } }
    }

    override suspend fun clear() {
        storage { dataStore.updateData { emptyMap() } }
    }

    private inline fun <T> storage(block: () -> T): T =
        try {
            block()
        } catch (failure: IOException) {
            throw failure.asSecretStoreException()
        }
}

private fun Throwable.asSecretStoreException(): Throwable =
    if (this is IOException) SecretStoreException("Secret storage failed: $message", this) else this

// `java.nio.file` needs API 26; `mkdirs` is the common denominator with Android's minSdk 24.
private fun File.ensureDirectory() {
    check(mkdirs() || isDirectory) { "Could not create secure storage directory: $absolutePath" }
}

private class LazySerializer<T>(create: () -> Serializer<T>) : Serializer<T> {
    private val delegate by lazy(create)

    override val defaultValue: T
        get() = delegate.defaultValue

    override suspend fun readFrom(input: InputStream): T = delegate.readFrom(input)

    override suspend fun writeTo(t: T, output: OutputStream) = delegate.writeTo(t, output)
}
