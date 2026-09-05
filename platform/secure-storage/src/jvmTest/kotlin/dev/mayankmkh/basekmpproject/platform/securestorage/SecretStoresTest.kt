package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalKermitApi::class)
class SecretStoresTest {
    private val logs = TestLogWriter(Severity.Verbose)
    private val logger = Logger(TestConfig(Severity.Verbose, listOf(logs)))

    // Exactly the logger the production factory hands the stores it opens: tagged once, in the
    // factory's constructor. Building the factory opens nothing.
    private val storeLogger =
        PlatformSecretStores(PlatformContext("secure-storage-tag-test"), logger).logger

    @Test
    fun `opening the same secret store twice fails immediately and names it`() {
        // Nothing is read, so the desktop directory is never created on the test machine.
        val stores = secretStores(PlatformContext("secure-storage-registry-test"), Logger)
        val name = "registry-${System.nanoTime()}"
        stores.open(name)

        val failure = assertFailsWith<IllegalStateException> { stores.open(name) }

        assertTrue(failure.message.orEmpty().contains(name))
    }

    /**
     * The tag is applied once, in the factory's constructor, and every store it opens writes under
     * it. The corruption handler is the shortest real line to check it against.
     */
    @Test
    fun `stores the factory opens write lines tagged with the module`() = runTest {
        val file = Files.createTempDirectory("secret-store-tag").resolve("tag.secrets")
        Files.write(file, byteArrayOf(1, 2, 3, 4))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store =
            dataStoreSecretStore(
                name = "tag",
                logger = storeLogger,
                produceSerializer = { MapStringSerializer },
                scope = scope,
            ) {
                file.toFile()
            }

        assertNull(store.get("token"))

        val entry = logs.logs.single()
        assertEquals(Severity.Warn, entry.severity)
        assertEquals("secure-storage", entry.tag)
        scope.coroutineContext[Job]?.cancelAndJoin()
    }
}
