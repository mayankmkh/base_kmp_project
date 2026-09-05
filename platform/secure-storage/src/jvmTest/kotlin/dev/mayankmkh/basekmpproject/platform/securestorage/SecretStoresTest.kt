package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecretStoresTest {
    @Test
    fun `opening the same secret store twice fails immediately and names it`() {
        // Nothing is read, so the desktop directory is never created on the test machine.
        val stores = secretStores(PlatformContext("secure-storage-registry-test"), Logger)
        val name = "registry-${System.nanoTime()}"
        stores.open(name)

        val failure = assertFailsWith<IllegalStateException> { stores.open(name) }

        assertTrue(failure.message.orEmpty().contains(name))
    }
}
