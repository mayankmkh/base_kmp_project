package dev.mayankmkh.basekmpproject.foundation.preferences

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalKermitApi::class)
class PreferenceCorruptionTest {
    private val logs = TestLogWriter(Severity.Verbose)
    private val logger = Logger(TestConfig(Severity.Verbose, listOf(logs)))

    private val storeLogger = logger.withTag("preferences")

    @Test
    fun `a corrupt preference file is replaced with empty preferences`() = runTest {
        withCorruptedStore { reopened ->
            val key = stringPrefKey("token")
            assertNull(reopened.get(key))
            reopened.set(key, "fresh")
            assertEquals("fresh", reopened.get(key))
        }
    }

    /**
     * The replacement in section 5 costs the user that file. One warning names the file and the
     * failure class underneath, under the tag the factory applied; the contents that could not be
     * read never reach the log.
     */
    @Test
    fun `replacing a corrupt preference file warns once`() = runTest {
        withCorruptedStore { reopened ->
            assertNull(reopened.get(stringPrefKey("token")))
            assertNull(reopened.get(stringPrefKey("other")))
        }

        val entry = logs.logs.single()
        assertEquals(Severity.Warn, entry.severity)
        assertEquals("preferences", entry.tag)
        assertTrue(entry.message.contains("file=corrupt"), entry.message)
        assertFalse(entry.message.contains("kept"), entry.message)
    }

    @Test
    fun `opening the same logical file twice fails immediately and names it`() {
        // Nothing is read, so the desktop directory is never created on the test machine.
        val stores = preferenceStores(PlatformContext("preferences-registry-test"), logger)
        val file = PrefFile("registry-${System.nanoTime()}")
        stores.open(file)

        val failure = assertFailsWith<IllegalStateException> { stores.open(file) }

        assertTrue(failure.message.orEmpty().contains(file.name))
    }

    private fun TestScope.storeScope() =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())

    /**
     * Writes "kept" through the production builder, ends that instance, overwrites the file on disk
     * with bytes no serializer accepts, reopens it and hands the reopened store to [block]. The
     * file lives where the desktop actual puts it and is removed afterwards.
     */
    private suspend fun TestScope.withCorruptedStore(block: suspend (PreferenceStore) -> Unit) {
        val unique = System.nanoTime()
        val context = PlatformContext("preferences-corruption-test-$unique")
        val file = PrefFile("corrupt-$unique")
        val directory = applicationDataDirectory(context.applicationId)
        try {
            val firstScope = storeScope()
            openStore(context, file, firstScope).set(stringPrefKey("token"), "kept")
            firstScope.coroutineContext[Job]?.cancelAndJoin()
            Files.write(
                directory.resolve("datastore").resolve(file.preferencesFileName).toPath(),
                byteArrayOf(1, 2, 3, 4),
            )

            val secondScope = storeScope()
            block(openStore(context, file, secondScope))
            secondScope.coroutineContext[Job]?.cancelAndJoin()
        } finally {
            directory.deleteRecursively()
        }
    }

    // The production builder, over the factory's own tagged logger, so the store under test is the
    // one a real store is.
    private fun openStore(context: PlatformContext, file: PrefFile, scope: CoroutineScope) =
        DataStorePreferenceStore(preferenceDataStore(context, file, storeLogger, scope))
}
