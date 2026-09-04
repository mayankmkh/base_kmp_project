package dev.mayankmkh.basekmpproject.platform.securestorage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class DesktopKeysetVaultTest {
    @Test
    fun `one application keyset encrypts every store and store names remain bound`() = runTest {
        val directory = Files.createTempDirectory("desktop-keyset-vault")
        val vault = InMemoryKeysetVault()
        val firstFile = directory.resolve("first.secrets").toFile()
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val first = encryptedStore("credentials", vault, firstFile, firstScope)

        assertNull(vault.json)
        first.set("token", "secret")
        assertNotNull(vault.json)
        assertEquals(1, vault.writes)
        firstScope.coroutineContext[Job]?.cancelAndJoin()

        val secondFile = directory.resolve("second.secrets").toFile()
        Files.copy(firstFile.toPath(), secondFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val secondScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val second = encryptedStore("credentials", vault, secondFile, secondScope)

        assertEquals("secret", second.get("token"))
        assertEquals(1, vault.writes)
        secondScope.coroutineContext[Job]?.cancelAndJoin()

        val swappedFile = directory.resolve("swapped.secrets").toFile()
        Files.copy(firstFile.toPath(), swappedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val swappedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val swapped = encryptedStore("other", vault, swappedFile, swappedScope)

        assertNull(swapped.get("token"))
        assertEquals(1, vault.writes)
        swappedScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private fun encryptedStore(
        name: String,
        vault: KeysetVault,
        file: File,
        scope: CoroutineScope,
    ): SecretStore =
        dataStoreSecretStore(
            produceSerializer = { encryptedSerializer(name, vault) },
            scope = scope,
            produceFile = { file },
        )

    private class InMemoryKeysetVault : KeysetVault {
        var json: String? = null
        var writes: Int = 0

        override fun read(): String? = json

        override fun write(json: String) {
            this.json = json
            writes++
        }
    }
}
