package dev.mayankmkh.basekmpproject.foundation.preferences

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * A store's path is a persistence contract (`PrefFile`), and the storage seam in section 4 is the
 * only thing that decides it. This pins the desktop end of that contract to the directory function
 * `:foundation:runtime` owns, so a change to how the DataStore above it is assembled cannot move
 * the file without failing here.
 */
class StorePathTest {
    @Test
    fun `a desktop preference file lands in the application data directory`() = runTest {
        // A name of its own: the factory registers every file it opens for the life of the
        // process (section 6), and this one really is opened.
        val unique = System.nanoTime()
        val applicationId = "preferences-path-test-$unique"
        val applicationDirectory = applicationDataDirectory(applicationId)
        try {
            val file = PrefFile("path-$unique")
            val store = preferenceStores(PlatformContext(applicationId), Logger).open(file)

            store.set(stringPrefKey("token"), "kept")

            val expected =
                applicationDirectory.resolve("datastore").resolve(file.preferencesFileName)
            assertTrue(expected.isFile, "Expected a preferences file at $expected")
        } finally {
            applicationDirectory.deleteRecursively()
        }
    }
}
