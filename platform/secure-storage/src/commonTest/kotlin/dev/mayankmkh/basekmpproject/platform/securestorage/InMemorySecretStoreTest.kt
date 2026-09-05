package dev.mayankmkh.basekmpproject.platform.securestorage

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class InMemorySecretStoreTest {
    @Test
    fun `values can be observed through their whole lifetime`() = runTest {
        val store = inMemorySecretStore()

        store.observe("token").test {
            assertNull(awaitItem())
            store.set("token", "first")
            assertEquals("first", awaitItem())
            store.set("token", "second")
            assertEquals("second", awaitItem())
            store.remove("token")
            assertNull(awaitItem())
        }
    }

    @Test
    fun `clear removes every value`() = runTest {
        val store = inMemorySecretStore()
        store.set("one", "1")
        store.set("two", "2")

        store.clear()

        assertNull(store.get("one"))
        assertNull(store.get("two"))
    }

    /**
     * The production factory registers each name for the life of the process. The in-memory factory
     * registers nothing, so a suite that starts the graph once per test opens the same names again
     * and again.
     */
    @Test
    fun `the in-memory factory registers no store name`() = runTest {
        val stores = inMemorySecretStores()

        stores.open("identity.credentials").set("token", "first")
        val second = stores.open("identity.credentials")

        assertNull(second.get("token"))
    }

    @Test
    fun `replaceAll swaps the whole snapshot in one emission`() = runTest {
        val store = MemorySecretStore()
        store.set("stale", "x")

        store.observe("token").test {
            assertNull(awaitItem())
            store.replaceAll(mapOf("token" to "loaded"))
            assertEquals("loaded", awaitItem())
            assertNull(store.get("stale"))
        }
    }
}
