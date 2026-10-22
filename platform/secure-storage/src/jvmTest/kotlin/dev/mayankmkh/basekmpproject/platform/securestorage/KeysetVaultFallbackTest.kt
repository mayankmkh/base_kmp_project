package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Assume.assumeFalse

@OptIn(ExperimentalKermitApi::class)
class KeysetVaultFallbackTest {
    private val directories = mutableListOf<File>()

    @AfterTest
    fun deleteDirectories() {
        directories.forEach { it.deleteRecursively() }
    }

    /**
     * Losing the OS vault means the keyset sits in a cleartext file, which an operator has to know
     * about. Every store opened afterwards asks the same memoised vault, so the answer is said once
     * rather than once per store.
     */
    @Test
    fun `an operating system with no vault warns once`() {
        val logs = TestLogWriter(Severity.Verbose)
        val logger = Logger(TestConfig(Severity.Verbose, listOf(logs)))
        val directory = temporaryDirectory("keyset-vault-fallback")
        val applicationId = "keyset-fallback-${System.nanoTime()}"
        val osName = System.getProperty("os.name")
        System.setProperty("os.name", "Plan 9")

        val vault =
            try {
                val first = applicationKeysetVault(applicationId, directory, logger)
                val second = applicationKeysetVault(applicationId, directory, logger)
                assertSame(first, second)
                first
            } finally {
                System.setProperty("os.name", osName)
            }

        assertTrue(vault is FileKeysetVault)
        val entry = logs.logs.single()
        assertEquals(Severity.Warn, entry.severity)
        assertTrue(entry.message.contains("Plan 9"), entry.message)
        assertTrue(entry.message.contains("cleartext keyset file"), entry.message)
    }

    /**
     * A keyset found in the cleartext file is the one in use from then on, whatever the OS vault
     * holds, and an operator learns that once, not at every read.
     */
    @Test
    fun `a keyset left in the file by an outage is used and reported once`() {
        val logs = TestLogWriter(Severity.Verbose)
        val directory = temporaryDirectory("keyset-vault-file-first")
        val file = FileKeysetVault(fallbackKeysetFile(directory))
        file.write("{\"keyset\":\"from the outage\"}")
        val osVault =
            object : KeysetVault {
                var reads = 0

                override fun read(): String {
                    reads++
                    return "{\"keyset\":\"older\"}"
                }

                override fun write(json: String) = error("the OS vault was written")
            }
        val vault =
            FallbackKeysetVault(osVault, file, Logger(TestConfig(Severity.Verbose, listOf(logs)))) {
                true
            }

        repeat(2) { assertEquals("{\"keyset\":\"from the outage\"}", vault.read()) }

        assertEquals(0, osVault.reads)
        val entry = logs.logs.single()
        assertEquals(Severity.Warn, entry.severity)
        assertTrue(entry.message.contains("earlier outage"), entry.message)
    }

    /**
     * Windows keeps the keyset DPAPI-protected in a file of its own. When DPAPI cannot load (here:
     * not on Windows at all), the keyset falls back to a cleartext file, which must be a different
     * one: read as cleartext, a DPAPI blob is no keyset, and a cleartext write over it would leave
     * DPAPI unable to unprotect the keyset once it loads again.
     */
    @Test
    fun `the cleartext fallback never writes over the DPAPI keyset`() {
        // On Windows DPAPI loads, so nothing here would fall back.
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val directory = temporaryDirectory("keyset-vault-windows")
        val applicationId = "keyset-windows-${System.nanoTime()}"
        val osName = System.getProperty("os.name")
        System.setProperty("os.name", "Windows 11")
        val vault =
            try {
                applicationKeysetVault(
                    applicationId,
                    directory,
                    Logger(TestConfig(Severity.Error, emptyList())),
                )
            } finally {
                System.setProperty("os.name", osName)
            }

        vault.write("{\"keyset\":\"cleartext\"}")

        assertTrue(fallbackKeysetFile(directory).isFile)
        assertFalse(
            dpapiKeysetFile(directory).exists(),
            "the DPAPI keyset file was written in cleartext",
        )
        assertEquals("{\"keyset\":\"cleartext\"}", vault.read())
    }

    /**
     * A vault library that cannot load is an outage from the first call, not a reason to pick the
     * file up front: with secrets on disk the read refuses, and only a fresh install starts its
     * keyset in the file. libsecret stands in here, as it cannot load anywhere but Linux.
     */
    @Test
    fun `a vault library that cannot load goes through the outage rule`() {
        val realOsName = System.getProperty("os.name")
        assumeFalse(realOsName.lowercase().contains("linux"))
        val withSecrets = temporaryDirectory("keyset-vault-unloadable")
        withSecrets.resolve("identity.credentials$SecretsFileSuffix").writeBytes(byteArrayOf(1))
        val fresh = temporaryDirectory("keyset-vault-unloadable-fresh")
        System.setProperty("os.name", "Linux")
        val (refusing, standingIn) =
            try {
                val logger = Logger(TestConfig(Severity.Error, emptyList()))
                applicationKeysetVault("unloadable-${System.nanoTime()}", withSecrets, logger) to
                    applicationKeysetVault("unloadable-fresh-${System.nanoTime()}", fresh, logger)
            } finally {
                System.setProperty("os.name", realOsName)
            }

        assertFailsWith<SecretStoreException> { refusing.read() }
        assertFalse(fallbackKeysetFile(withSecrets).exists())

        assertEquals(null, standingIn.read())
        standingIn.write("{\"keyset\":\"fresh\"}")
        assertEquals("{\"keyset\":\"fresh\"}", FileKeysetVault(fallbackKeysetFile(fresh)).read())
    }

    /**
     * The other half: with the protected keyset and the secrets under it on disk, a DPAPI that
     * cannot load leaves the cleartext file absent, so the read refuses instead of taking the blob
     * for a keyset or starting a new one.
     */
    @Test
    fun `a DPAPI outage with the protected keyset on disk refuses the read`() {
        val directory = temporaryDirectory("keyset-vault-windows-outage")
        val protectedKeyset = ByteArray(230) { it.toByte() }
        dpapiKeysetFile(directory).writeBytes(protectedKeyset)
        directory.resolve("identity.credentials$SecretsFileSuffix").writeBytes(byteArrayOf(1))
        val osName = System.getProperty("os.name")
        System.setProperty("os.name", "Windows 11")
        val vault =
            try {
                applicationKeysetVault(
                    "keyset-windows-outage-${System.nanoTime()}",
                    directory,
                    Logger(TestConfig(Severity.Error, emptyList())),
                )
            } finally {
                System.setProperty("os.name", osName)
            }

        assertFailsWith<SecretStoreException> { vault.read() }

        assertFalse(fallbackKeysetFile(directory).exists())
        assertContentEquals(protectedKeyset, dpapiKeysetFile(directory).readBytes())
    }

    /**
     * Only a cleartext keyset in the file is adopted. Anything else there is a protected keyset
     * that has not moved yet, and adopting it would make it the keyset for the rest of the process,
     * so the read refuses and leaves the file as it is.
     */
    @Test
    fun `a keyset file that is not cleartext is never adopted`() {
        val directory = temporaryDirectory("keyset-vault-not-cleartext")
        val protectedKeyset = ByteArray(230) { it.toByte() }
        fallbackKeysetFile(directory).writeBytes(protectedKeyset)
        val osVault =
            object : KeysetVault {
                override fun read(): String = "{\"keyset\":\"current\"}"

                override fun write(json: String) = error("the OS vault was written")
            }
        val vault =
            FallbackKeysetVault(
                osVault,
                FileKeysetVault(fallbackKeysetFile(directory)),
                Logger(TestConfig(Severity.Error, emptyList())),
            ) {
                true
            }

        assertFailsWith<SecretStoreException> { vault.read() }

        assertContentEquals(protectedKeyset, fallbackKeysetFile(directory).readBytes())
    }

    /**
     * A cleartext keyset damaged on disk no longer parses, so it reads like a protected one. Off
     * Windows nothing moves it: the read refuses, names the file and writes nothing. The Windows
     * move, which runs before the vault on that OS only, takes it for the protected keyset and puts
     * it in the DPAPI file, so even there it is never adopted as cleartext.
     */
    @Test
    fun `a corrupted cleartext keyset is refused in place and only the Windows move takes it`() {
        val directory = temporaryDirectory("keyset-vault-corrupted-cleartext")
        val corrupted = "{\"primaryKeyId\":1,\"key\":["
        fallbackKeysetFile(directory).writeText(corrupted)
        val osVault =
            object : KeysetVault {
                override fun read(): String = "{\"keyset\":\"current\"}"

                override fun write(json: String) = error("the OS vault was written")
            }
        val vault =
            FallbackKeysetVault(
                osVault,
                FileKeysetVault(fallbackKeysetFile(directory)),
                Logger(TestConfig(Severity.Error, emptyList())),
            ) {
                true
            }

        val refusal = assertFailsWith<SecretStoreException> { vault.read() }

        assertTrue(
            refusal.message.orEmpty().contains(fallbackKeysetFile(directory).absolutePath),
            "The refusal does not say where the file is: ${refusal.message}",
        )
        assertEquals(corrupted, fallbackKeysetFile(directory).readText())
        assertFalse(dpapiKeysetFile(directory).exists())

        moveSharedDpapiKeyset(directory)

        assertFalse(fallbackKeysetFile(directory).exists())
        assertEquals(corrupted, dpapiKeysetFile(directory).readText())
    }

    private fun temporaryDirectory(prefix: String): File =
        Files.createTempDirectory(prefix).toFile().also(directories::add)
}
