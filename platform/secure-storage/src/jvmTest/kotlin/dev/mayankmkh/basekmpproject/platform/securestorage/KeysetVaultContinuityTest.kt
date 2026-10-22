package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

/**
 * Each `process` below is a fresh vault and store over the same directory, as a restart would be:
 * the keyset, wherever it lives, must keep decrypting the secrets already on disk.
 */
class KeysetVaultContinuityTest {
    private val directory: File = Files.createTempDirectory("keyset-continuity").toFile()
    private val nativeVault = SwitchableVault()

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun `an unavailable OS vault holding the keyset leaves existing secrets in place`() = runTest {
        process { it.set("token", "keep") }
        val secrets = directory.resolve("$StoreName$SecretsFileSuffix").readBytes()

        nativeVault.unavailable = true
        process { store ->
            assertFailsWith<SecretStoreException> { store.get("token") }
            // Not a reason to start again: no new keyset, and the ciphertext is untouched.
            assertNull(fileVault().read())
            assertContentEquals(
                secrets,
                directory.resolve("$StoreName$SecretsFileSuffix").readBytes(),
            )

            nativeVault.unavailable = false
            assertEquals("keep", store.get("token"))
        }
    }

    @Test
    fun `a keyset written to the file while the OS vault was unavailable stays in use`() = runTest {
        nativeVault.unavailable = true
        process { it.set("token", "keep") }

        nativeVault.unavailable = false
        process { assertEquals("keep", it.get("token")) }
        assertNull(nativeVault.json)
    }

    /**
     * A first launch that only reads puts a keyset in the OS vault without writing any secret, so
     * an outage at the next launch finds no secrets and mints a second keyset in the file. Whatever
     * is stored from then on is under the file's keyset; the OS vault answering again with its
     * older one must not decide which keyset the secrets are read with.
     */
    @Test
    fun `secrets stored under a keyset minted in an outage survive the OS vault returning`() =
        runTest {
            process { assertNull(it.get("token")) }
            val osKeyset = assertNotNull(nativeVault.json)

            nativeVault.unavailable = true
            process { it.set("token", "keep") }

            nativeVault.unavailable = false
            process { assertEquals("keep", it.get("token")) }
            nativeVault.unavailable = true
            process { assertEquals("keep", it.get("token")) }
            assertEquals(osKeyset, nativeVault.json)
        }

    /** The same two keysets, with the outage over before anything was stored. */
    @Test
    fun `secrets stored after an outage minted a keyset survive the next outage`() = runTest {
        process { assertNull(it.get("token")) }
        nativeVault.unavailable = true
        process { assertNull(it.get("token")) }

        nativeVault.unavailable = false
        process { it.set("token", "keep") }
        nativeVault.unavailable = true
        process { assertEquals("keep", it.get("token")) }
        nativeVault.unavailable = false
        process { assertEquals("keep", it.get("token")) }
    }

    /**
     * "No secrets yet" is what lets the file stand in, so a directory that merely cannot be listed
     * must not read as one without secrets. Without read permission the files still open by name,
     * as the secrets would, but the listing fails.
     */
    @Test
    fun `a directory that cannot be listed is not taken for one without secrets`() = runTest {
        process { it.set("token", "keep") }
        nativeVault.unavailable = true
        val vault =
            FallbackKeysetVault(nativeVault, fileVault(), Logger) {
                directory.hasEncryptedSecrets()
            }

        directory.setReadable(false)
        try {
            // Root, or a file system without permission bits, lists it anyway: nothing to test.
            assumeTrue(directory.list() == null)
            assertFailsWith<SecretStoreException> { vault.read() }
        } finally {
            directory.setReadable(true)
        }
        assertNull(fileVault().read())
        assertFalse(directory.resolve("missing").hasEncryptedSecrets())
    }

    /**
     * A damaged keyset is a store that cannot be read, so it must say so in the one type callers
     * catch: Tink's base-64 decoder rejects a corrupted key value with IllegalArgumentException,
     * which would otherwise crash the launch that reads the session.
     */
    @Test
    fun `a keyset whose key value is damaged is a secret store failure`() = runTest {
        process { it.set("token", "keep") }
        nativeVault.json =
            assertNotNull(nativeVault.json)
                .replace(Regex("\"value\":\"[^\"]*\""), "\"value\":\"not base-64!\"")

        process { store ->
            assertFailsWith<SecretStoreException> { store.get("token") }
            assertFailsWith<SecretStoreException> { store.set("token", "other") }
        }
    }

    private suspend fun TestScope.process(block: suspend (SecretStore) -> Unit) {
        val vault =
            FallbackKeysetVault(nativeVault, fileVault(), Logger) {
                directory.hasEncryptedSecrets()
            }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        try {
            block(
                dataStoreSecretStore(
                    name = StoreName,
                    logger = Logger,
                    produceSerializer = { encryptedSerializer(StoreName, vault) },
                    scope = scope,
                    produceFile = { directory.resolve("$StoreName$SecretsFileSuffix") },
                )
            )
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    private fun fileVault() = FileKeysetVault(directory.resolve("secure-storage.keyset"))

    private class SwitchableVault : KeysetVault {
        var json: String? = null
        var unavailable = false

        override fun read(): String? {
            if (unavailable) {
                throw KeysetVaultUnavailableException(
                    "native vault is down",
                    IllegalStateException(),
                )
            }
            return json
        }

        override fun write(json: String) {
            if (unavailable) {
                throw KeysetVaultUnavailableException(
                    "native vault is down",
                    IllegalStateException(),
                )
            }
            this.json = json
        }
    }

    private companion object {
        const val StoreName = "credentials"
    }
}
