package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PreferenceStoreTest {
    private val booleanKey = booleanPrefKey("boolean")
    private val intKey = intPrefKey("int")
    private val longKey = longPrefKey("long")
    private val floatKey = floatPrefKey("float")
    private val doubleKey = doublePrefKey("double")
    private val stringKey = stringPrefKey("string")
    private val stringSetKey = stringSetPrefKey("string-set")

    @Test
    fun `all supported key types round trip without parsing`() = runTest {
        val store = inMemoryPreferenceStore()

        store.set(booleanKey, true)
        store.set(intKey, 7)
        store.set(longKey, 8L)
        store.set(floatKey, 1.5F)
        store.set(doubleKey, 2.5)
        store.set(stringKey, "value")
        store.set(stringSetKey, setOf("one", "two"))

        assertEquals(true, store.get(booleanKey))
        assertEquals(7, store.get(intKey))
        assertEquals(8L, store.get(longKey))
        assertEquals(1.5F, store.get(floatKey))
        assertEquals(2.5, store.get(doubleKey))
        assertEquals("value", store.get(stringKey))
        assertEquals(setOf("one", "two"), store.get(stringSetKey))
    }

    @Test
    fun `edit publishes one snapshot containing every change`() = runTest {
        val dataStore = InMemoryDataStore(emptyPreferences())
        val store = DataStorePreferenceStore(dataStore)

        dataStore.data.test {
            assertTrue(awaitItem().asMap().isEmpty())

            store.edit {
                set(stringKey, "value")
                set(booleanKey, true)
            }

            val changed = awaitItem()
            assertEquals("value", changed[stringKey.dataStoreKey])
            assertEquals(true, changed[booleanKey.dataStoreKey])
            expectNoEvents()
        }
    }

    @Test
    fun `a key stays quiet while an unrelated key changes`() = runTest {
        val store = inMemoryPreferenceStore()

        store.observe(stringKey).test {
            assertNull(awaitItem())
            store.set(booleanKey, true)
            store.set(stringKey, "value")
            assertEquals("value", awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `remove and clear delete values rather than blanking them`() = runTest {
        val store = inMemoryPreferenceStore()
        store.set(stringKey, "value")
        store.set(booleanKey, true)

        store.remove(stringKey)
        assertNull(store.get(stringKey))
        assertFalse(store.contains(stringKey))
        assertTrue(store.contains(booleanKey))

        store.clear()
        assertFalse(store.contains(booleanKey))
    }
}
