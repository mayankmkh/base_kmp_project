package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

class PreferenceCorruptionTest {
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

    @Test
    fun `opening the same logical file twice fails immediately and names it`() {
        val context = PreferencesContext("preferences-registry-test")
        val file = PrefFile("registry-${System.nanoTime()}")
        openPreferenceStore(context, file)

        val failure = assertFailsWith<IllegalStateException> { openPreferenceStore(context, file) }

        assertTrue(failure.message.orEmpty().contains(file.name))
    }

    private fun TestScope.storeScope() =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())

    private fun openStore(file: Path, scope: CoroutineScope): PreferenceStore =
        DataStorePreferenceStore(
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file.toAbsolutePath().toString().toPath() },
            )
        )
}
