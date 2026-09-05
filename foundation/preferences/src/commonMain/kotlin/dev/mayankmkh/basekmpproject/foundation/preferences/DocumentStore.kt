package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

public interface DocumentStore<T> {
    public val data: Flow<T>

    public suspend fun update(transform: (T) -> T): T
}

internal fun <T> inMemoryDocumentStore(defaultValue: T): DocumentStore<T> =
    DataStoreDocumentStore(InMemoryDataStore(defaultValue))

internal class DataStoreDocumentStore<T>(private val dataStore: DataStore<T>) : DocumentStore<T> {
    override val data: Flow<T> = dataStore.data

    override suspend fun update(transform: (T) -> T): T = dataStore.updateData { transform(it) }
}

internal class JsonDocumentSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : OkioSerializer<T> {
    override suspend fun readFrom(source: BufferedSource): T =
        try {
            DocumentJson.decodeFromString(serializer, source.readUtf8())
        } catch (exception: SerializationException) {
            throw CorruptionException("Stored document is not valid JSON.", exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException("Stored document does not match its serializer.", exception)
        }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.writeUtf8(DocumentJson.encodeToString(serializer, t))
    }
}

// Persisted files must survive both upgrades and downgrades. Network JSON is deliberately stricter
// because a server contract has different failure semantics from app-owned data already on disk.
private val DocumentJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
}
