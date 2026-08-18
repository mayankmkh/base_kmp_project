package dev.mayankmkh.basekmpproject.shared.libs.prefs

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.shared.libs.prefs.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class KeyValueStoreTest {

    private val store = KeyValueStore(InMemoryPreferencesDataStore(), Json)

    @Test
    fun `there are no items before anything is saved`() = runTest {
        assertNull(store.getItems())
    }

    @Test
    fun `saved items survive the round trip through json`() = runTest {
        store.saveItems(items)

        assertEquals(items, store.getItems())
    }

    @Test
    fun `saving replaces the previous items rather than appending`() = runTest {
        store.saveItems(items)

        store.saveItems(listOf(items.first()))

        assertEquals(listOf(items.first()), store.getItems())
    }

    @Test
    fun `the flow emits absence and then what was saved`() = runTest {
        store.getItemsFlow().test {
            assertNull(awaitItem())

            store.saveItems(items)

            assertEquals(items, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val items =
            listOf(ItemEntity("1", "First", "First body"), ItemEntity("2", "Second", "Second body"))
    }
}
