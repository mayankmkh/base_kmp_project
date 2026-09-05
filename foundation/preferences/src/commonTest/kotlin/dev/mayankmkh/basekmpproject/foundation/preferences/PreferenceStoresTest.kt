package dev.mayankmkh.basekmpproject.foundation.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer

class PreferenceStoresTest {
    private val file = PrefFile("settings")

    /**
     * The production factory registers each file name for the life of the process (section 6). The
     * in-memory factory registers nothing, so a suite that starts the graph once per test opens the
     * same names again and again.
     */
    @Test
    fun `the in-memory factory registers no file name`() = runTest {
        val stores = inMemoryPreferenceStores()
        val key = stringPrefKey("token")

        stores.open(file).set(key, "first")
        val second = stores.open(file)

        assertNull(second.get(key))
    }

    @Test
    fun `the in-memory factory registers no document file name`() = runTest {
        val stores = inMemoryPreferenceStores()

        stores.openDocument(file, Int.serializer(), 1).update { it + 1 }
        val second = stores.openDocument(file, Int.serializer(), 1)

        assertEquals(1, second.data.first())
    }
}
