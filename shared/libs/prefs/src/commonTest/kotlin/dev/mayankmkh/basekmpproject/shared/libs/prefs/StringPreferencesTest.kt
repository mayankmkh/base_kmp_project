package dev.mayankmkh.basekmpproject.shared.libs.prefs

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.shared.libs.prefs.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StringPreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()

    @Test
    fun `an unwritten key reads as absent`() = runTest {
        assertNull(dataStore.getStringOrNull(TestKey.VALUE))
        assertFalse(dataStore.hasKey(TestKey.VALUE))
    }

    @Test
    fun `a written key reads back`() = runTest {
        dataStore.putString(TestKey.VALUE, "written")

        assertEquals("written", dataStore.getStringOrNull(TestKey.VALUE))
        assertTrue(dataStore.hasKey(TestKey.VALUE))
    }

    @Test
    fun `remove takes the key out rather than blanking it`() = runTest {
        dataStore.putString(TestKey.VALUE, "written")

        dataStore.remove(TestKey.VALUE)

        assertNull(dataStore.getStringOrNull(TestKey.VALUE))
        assertFalse(dataStore.hasKey(TestKey.VALUE))
    }

    @Test
    fun `a key's flow stays quiet while another key changes`() = runTest {
        dataStore.getStringOrNullFlow(TestKey.VALUE).test {
            assertNull(awaitItem())

            dataStore.putString(TestKey.OTHER, "unrelated")
            dataStore.putString(TestKey.VALUE, "written")

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
