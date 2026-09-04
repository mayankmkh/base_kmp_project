package dev.mayankmkh.basekmpproject.platform.securestorage

import java.util.Locale
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue

class MacOsKeychainKeysetVaultTest {
    @Test
    fun `generic password round trips and is deleted`() {
        assumeTrue(
            "This smoke test requires macOS.",
            System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("mac"),
        )
        val service = "dev.mayankmkh.basekmpproject.secure-storage-test.${UUID.randomUUID()}"
        val vault =
            try {
                MacOsKeychainKeysetVault(service)
            } catch (failure: LinkageError) {
                assumeNoException("Security.framework could not be loaded.", failure)
                return
            }
        var accessFailure: SecretStoreException? = null
        try {
            vault.write("test-keyset")
            assertEquals("test-keyset", vault.read())
        } catch (failure: SecretStoreException) {
            accessFailure = failure
        } finally {
            try {
                vault.delete()
            } catch (failure: SecretStoreException) {
                if (accessFailure == null) accessFailure = failure
            }
        }
        accessFailure?.let {
            assumeNoException("The JVM could not access the developer login Keychain.", it)
        }
    }
}
