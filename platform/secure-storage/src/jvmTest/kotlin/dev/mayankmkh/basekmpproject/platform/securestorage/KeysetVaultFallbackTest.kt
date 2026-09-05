package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalKermitApi::class)
class KeysetVaultFallbackTest {
    /**
     * Losing the OS vault means the keyset sits in a cleartext file, which an operator has to know
     * about. Every store opened afterwards asks the same memoised vault, so the answer is said once
     * rather than once per store.
     */
    @Test
    fun `an operating system with no vault warns once`() {
        val logs = TestLogWriter(Severity.Verbose)
        val logger = Logger(TestConfig(Severity.Verbose, listOf(logs)))
        val directory = Files.createTempDirectory("keyset-vault-fallback").toFile()
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
}
