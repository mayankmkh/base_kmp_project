package dev.mayankmkh.basekmpproject.foundation.preferences

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DataStorePreferenceStoreTest {

    private val store = inMemoryPreferenceStore()

    @Test
    fun `an unwritten key reads as absent`() = runTest {
        assertNull(store.getString(TestKey.VALUE))
        assertFalse(store.contains(TestKey.VALUE))
    }

    @Test
    fun `a written key reads back`() = runTest {
        store.putString(TestKey.VALUE, "written")

        assertEquals("written", store.getString(TestKey.VALUE))
        assertTrue(store.contains(TestKey.VALUE))
    }

    @Test
    fun `remove takes the key out rather than blanking it`() = runTest {
        store.putString(TestKey.VALUE, "written")

        store.remove(TestKey.VALUE)

        assertNull(store.getString(TestKey.VALUE))
        assertFalse(store.contains(TestKey.VALUE))
    }

    @Test
    fun `a key's flow stays quiet while another key changes`() = runTest {
        store.observeString(TestKey.VALUE).test {
            assertNull(awaitItem())

            store.putString(TestKey.OTHER, "unrelated")
            store.putString(TestKey.VALUE, "written")

            // A second `null` here would mean the unrelated write got through: the store re-emits
            // the whole snapshot, so the flow leans on `distinctUntilChanged` to filter it out.
            assertEquals("written", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private enum class TestKey(override val key: String) : PrefKey {
        VALUE("value"),
        OTHER("other"),
    }
}
