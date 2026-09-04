package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class MapStringSerializerTest {
    @Test
    fun `map serializer round trips every entry`() = runTest {
        val serializer = MapStringSerializer
        val output = ByteArrayOutputStream()
        serializer.writeTo(mapOf("token" to "secret", "refresh" to "other"), output)

        val restored = serializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertEquals(mapOf("token" to "secret", "refresh" to "other"), restored)
    }

    @Test
    fun `Tink encrypted store round trips`() = runTest {
        val file = Files.createTempDirectory("tink-round-trip").resolve("test.secrets").toFile()
        val scope = dataStoreScope(UnconfinedTestDispatcher(testScheduler))
        val store = dataStoreSecretStore({ encryptedSerializer() }, scope) { file }

        store.set("token", "secret")

        assertEquals("secret", store.get("token"))
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun `tampered ciphertext reopens as an empty store`() = runTest {
        val file = Files.createTempDirectory("tink-tamper").resolve("test.secrets").toFile()
        val aead = aead()
        val firstScope = dataStoreScope(UnconfinedTestDispatcher(testScheduler))
        val first = dataStoreSecretStore({ encryptedSerializer(aead) }, firstScope) { file }
        first.set("token", "secret")
        firstScope.coroutineContext[Job]?.cancelAndJoin()

        val ciphertext = Files.readAllBytes(file.toPath())
        ciphertext[ciphertext.lastIndex / 2] =
            (ciphertext[ciphertext.lastIndex / 2].toInt() xor 1).toByte()
        Files.write(file.toPath(), ciphertext)

        val secondScope = dataStoreScope(UnconfinedTestDispatcher(testScheduler))
        val reopened = dataStoreSecretStore({ encryptedSerializer(aead) }, secondScope) { file }

        assertNull(reopened.get("token"))
        secondScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private fun encryptedSerializer(aead: Aead = aead()) =
        AeadSerializer(aead, MapStringSerializer, AssociatedData)

    private fun aead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun dataStoreScope(dispatcher: CoroutineDispatcher) =
        CoroutineScope(dispatcher + SupervisorJob())

    private companion object {
        val AssociatedData = "test.secrets".encodeToByteArray()
    }
}
