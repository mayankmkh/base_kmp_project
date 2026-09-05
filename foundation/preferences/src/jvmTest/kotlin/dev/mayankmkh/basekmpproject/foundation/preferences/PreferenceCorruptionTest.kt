package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import java.nio.file.Files
import java.nio.file.Path
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
import okio.Path.Companion.toPath

@OptIn(ExperimentalKermitApi::class)
class PreferenceCorruptionTest {
    private val logs = TestLogWriter(Severity.Verbose)
    private val logger = Logger(TestConfig(Severity.Verbose, listOf(logs)))

    @Test
    fun `a corrupt preference file is replaced with empty preferences`() = runTest {
        val file =
            Files.createTempDirectory("preferences-corruption").resolve("corrupt.preferences_pb")
        val key = stringPrefKey("token")
        val firstScope = storeScope()
        openStore(file, firstScope).set(key, "kept")
        firstScope.coroutineContext[Job]?.cancelAndJoin()
        Files.write(file, byteArrayOf(1, 2, 3, 4))

        val secondScope = storeScope()
        val reopened = openStore(file, secondScope)

        assertNull(reopened.get(key))
        reopened.set(key, "fresh")
        assertEquals("fresh", reopened.get(key))
        secondScope.coroutineContext[Job]?.cancelAndJoin()
    }

    /**
     * The replacement in section 5 costs the user that file. One warning names the file and the
     * failure class underneath; the contents that could not be read never reach the log.
     */
    @Test
    fun `replacing a corrupt preference file warns once`() = runTest {
        val file =
            Files.createTempDirectory("preferences-corruption-log")
                .resolve("corrupt.preferences_pb")
        val firstScope = storeScope()
        openStore(file, firstScope).set(stringPrefKey("token"), "kept")
        firstScope.coroutineContext[Job]?.cancelAndJoin()
        Files.write(file, byteArrayOf(1, 2, 3, 4))

        val secondScope = storeScope()
        val reopened = openStore(file, secondScope)
        assertNull(reopened.get(stringPrefKey("token")))
        assertNull(reopened.get(stringPrefKey("other")))
        secondScope.coroutineContext[Job]?.cancelAndJoin()

        val entry = logs.logs.single()
        assertEquals(Severity.Warn, entry.severity)
        assertEquals(LogTag, entry.tag)
        assertTrue(entry.message.contains("file=corrupt"), entry.message)
        assertFalse(entry.message.contains("kept"), entry.message)
    }

    @Test
    fun `opening the same logical file twice fails immediately and names it`() {
        val context = PlatformContext("preferences-registry-test")
        val file = PrefFile("registry-${System.nanoTime()}")
        openPreferenceStore(context, file, logger)

        val failure =
            assertFailsWith<IllegalStateException> {
                openPreferenceStore(context, file, logger)
            }

        assertTrue(failure.message.orEmpty().contains(file.name))
    }

    private fun TestScope.storeScope() =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())

    // The production corruption handler over a file the test controls, so the warning under test is
    // the one a real store writes.
    private fun openStore(file: Path, scope: CoroutineScope): PreferenceStore =
        DataStorePreferenceStore(
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler =
                    replaceCorruptFile(
                        logger.withTag(LogTag),
                        PrefFile("corrupt"),
                        "preferences",
                    ) {
                        emptyPreferences()
                    },
                scope = scope,
                produceFile = { file.toAbsolutePath().toString().toPath() },
            )
        )
}
