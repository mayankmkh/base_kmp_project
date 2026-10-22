package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeFalse

/**
 * Windows installs from before the DPAPI keyset had a file of its own keep the protected keyset in
 * the file the cleartext fallback now owns. [FakeDpapi] stands in for DPAPI, which only Windows
 * has.
 */
class DpapiKeysetMoveTest {
    private val directory: File = Files.createTempDirectory("dpapi-keyset-move").toFile()

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun `a protected keyset in the shared file moves to the DPAPI file`() {
        val blob = "$ProtectedMarker{\"keyset\":\"protected\"}".encodeToByteArray()
        fallbackKeysetFile(directory).writeBytes(blob)

        moveSharedDpapiKeyset(directory)

        assertFalse(fallbackKeysetFile(directory).exists())
        assertContentEquals(blob, dpapiKeysetFile(directory).readBytes())
        assertEquals("{\"keyset\":\"protected\"}", FakeDpapi(dpapiKeysetFile(directory)).read())
    }

    /** An outage under the old layout wrote cleartext there, and that keyset is the fallback's. */
    @Test
    fun `a cleartext keyset in the shared file stays where the fallback reads it`() {
        fallbackKeysetFile(directory).writeText("{\"keyset\":\"cleartext\"}")

        moveSharedDpapiKeyset(directory)

        assertEquals("{\"keyset\":\"cleartext\"}", fallbackKeysetFile(directory).readText())
        assertFalse(dpapiKeysetFile(directory).exists())
    }

    @Test
    fun `an existing DPAPI file is never replaced`() {
        val shared = "$ProtectedMarker{\"keyset\":\"older\"}".encodeToByteArray()
        val current = "$ProtectedMarker{\"keyset\":\"current\"}".encodeToByteArray()
        fallbackKeysetFile(directory).writeBytes(shared)
        dpapiKeysetFile(directory).writeBytes(current)

        moveSharedDpapiKeyset(directory)

        assertContentEquals(shared, fallbackKeysetFile(directory).readBytes())
        assertContentEquals(current, dpapiKeysetFile(directory).readBytes())
    }

    /**
     * The move does not need DPAPI, so even with DPAPI unloadable the blob leaves the shared file
     * and is never read as a cleartext keyset; the read then refuses as [SecretStoreException] and
     * writes nothing until DPAPI loads.
     */
    @Test
    fun `a protected keyset with DPAPI unavailable moves and refuses without writing`() {
        val realOsName = System.getProperty("os.name")
        assumeFalse(realOsName.lowercase().contains("win"))
        val blob = ByteArray(230) { it.toByte() }
        fallbackKeysetFile(directory).writeBytes(blob)
        directory.resolve("identity.credentials$SecretsFileSuffix").writeBytes(byteArrayOf(1))
        System.setProperty("os.name", "Windows 11")
        val vault =
            try {
                applicationKeysetVault(
                    "dpapi-move-outage-${System.nanoTime()}",
                    directory,
                    Logger(TestConfig(Severity.Error, emptyList())),
                )
            } finally {
                System.setProperty("os.name", realOsName)
            }

        assertFailsWith<SecretStoreException> { encryptedSerializer("identity.credentials", vault) }

        assertFalse(fallbackKeysetFile(directory).exists())
        assertContentEquals(blob, dpapiKeysetFile(directory).readBytes())
        assertTrue(directory.resolve("identity.credentials$SecretsFileSuffix").isFile)
    }

    /**
     * One DPAPI failure, when the protected keyset is still in the shared file, must not make that
     * blob the cleartext fallback's keyset for the rest of the process: the read refuses, nothing
     * switches to the file, and the next read gets the keyset from DPAPI.
     */
    @Test
    fun `a DPAPI failure never makes the protected keyset the fallback keyset`() {
        fallbackKeysetFile(directory).writeText("$ProtectedMarker{\"keyset\":\"protected\"}")
        var failuresLeft = 1
        val flakyDpapi = { file: File ->
            object : KeysetVault {
                override fun read(): String? {
                    if (failuresLeft > 0) {
                        failuresLeft--
                        throw SecretStoreException("Windows DPAPI keyset unprotect failed.")
                    }
                    return FakeDpapi(file).read()
                }

                override fun write(json: String) = FakeDpapi(file).write(json)
            }
        }

        moveSharedDpapiKeyset(directory)
        val vault =
            FallbackKeysetVault(
                flakyDpapi(dpapiKeysetFile(directory)),
                FileKeysetVault(fallbackKeysetFile(directory)),
                Logger(TestConfig(Severity.Error, emptyList())),
            ) {
                true
            }

        assertFailsWith<SecretStoreException> { vault.read() }
        assertEquals("{\"keyset\":\"protected\"}", vault.read())
        assertFalse(fallbackKeysetFile(directory).exists())
    }

    /** Unprotects only what it protected, as DPAPI rejects any other bytes. */
    private class FakeDpapi(private val file: File) : KeysetVault {
        override fun read(): String? {
            val stored = file.takeIf { it.isFile }?.readText() ?: return null
            if (!stored.startsWith(ProtectedMarker)) {
                throw SecretStoreException("Windows DPAPI keyset unprotect failed.")
            }
            return stored.removePrefix(ProtectedMarker)
        }

        override fun write(json: String) = file.writeText(ProtectedMarker + json)
    }

    private companion object {
        const val ProtectedMarker = "dpapi:"
    }
}
