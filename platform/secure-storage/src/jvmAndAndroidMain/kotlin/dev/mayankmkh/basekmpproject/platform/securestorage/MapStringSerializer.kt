package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/** The plaintext form of a secret store file: one JSON object of string values. */
@OptIn(ExperimentalSerializationApi::class)
internal object MapStringSerializer : Serializer<Map<String, String>> {
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override val defaultValue: Map<String, String> = emptyMap()

    override suspend fun readFrom(input: InputStream): Map<String, String> =
        try {
            Json.decodeFromStream(mapSerializer, input)
        } catch (exception: SerializationException) {
            throw CorruptionException("Stored secrets are not valid JSON.", exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException("Stored secrets do not match their serializer.", exception)
        }

    override suspend fun writeTo(t: Map<String, String>, output: OutputStream) {
        Json.encodeToStream(mapSerializer, t, output)
    }
}
