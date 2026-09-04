package dev.mayankmkh.basekmpproject.platform.securestorage

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenSecretStoreTest {
    @Test
    fun `opening the same secret store twice fails immediately and names it`() {
        // Nothing is read, so the desktop directory is never created on the test machine.
        val context = SecureStorageContext("secure-storage-registry-test")
        val name = "registry-${System.nanoTime()}"
        openSecretStore(context, name)

        val failure = assertFailsWith<IllegalStateException> { openSecretStore(context, name) }

        assertTrue(failure.message.orEmpty().contains(name))
    }
}
