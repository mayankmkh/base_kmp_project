package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.tink.AeadSerializer
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
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
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalKermitApi::class)
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
        val store =
            dataStoreSecretStore(
                name = StoreName,
                logger = Logger,
                produceSerializer = { encryptedSerializer() },
                scope = scope,
            ) {
                file
            }

        store.set("token", "secret")

        assertEquals("secret", store.get("token"))
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun `tampered ciphertext reopens as an empty store`() = runTest {
        val file = Files.createTempDirectory("tink-tamper").resolve("test.secrets").toFile()
        val aead = aead()
        val logs = TestLogWriter(Severity.Verbose)
        val logger = Logger(TestConfig(Severity.Verbose, listOf(logs)))
        val firstScope = dataStoreScope(UnconfinedTestDispatcher(testScheduler))
        val first =
            dataStoreSecretStore(
                name = StoreName,
                logger = logger,
                produceSerializer = { encryptedSerializer(aead) },
                scope = firstScope,
            ) {
                file
            }
        first.set("token", "secret")
        firstScope.coroutineContext[Job]?.cancelAndJoin()

        val ciphertext = Files.readAllBytes(file.toPath())
        ciphertext[ciphertext.lastIndex / 2] =
            (ciphertext[ciphertext.lastIndex / 2].toInt() xor 1).toByte()
        Files.write(file.toPath(), ciphertext)

        val secondScope = dataStoreScope(UnconfinedTestDispatcher(testScheduler))
        val reopened =
            dataStoreSecretStore(
                name = StoreName,
                logger = logger,
                produceSerializer = { encryptedSerializer(aead) },
                scope = secondScope,
            ) {
                file
            }

        assertNull(reopened.get("token"))
        // Signing the user out silently is what this line exists to prevent.
        val entry = logs.logs.single()
        assertEquals(Severity.Warn, entry.severity)
        assertTrue(entry.message.contains("name=$StoreName"), entry.message)
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
        const val StoreName = "test.secrets"
        val AssociatedData = StoreName.encodeToByteArray()
    }
}
