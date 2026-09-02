package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PostsLocalStoreTest {

    @Test
    fun `replaceAll round trips through the database in feed order`() = runTest {
        val store = createInMemoryPostsLocalStore()

        store.replaceAll(listOf(entity("10"), entity("2")))

        // "10" before "2" -- the point of storing an explicit position rather than sorting on the
        // text id, which would put "10" second.
        assertEquals(listOf("10", "2"), store.observeAll().first().map { it.id })
    }

    @Test
    fun `replaceAll drops rows the new feed no longer contains`() = runTest {
        val store = createInMemoryPostsLocalStore()
        store.replaceAll(listOf(entity("1"), entity("2")))

        store.replaceAll(listOf(entity("2")))

        assertEquals(listOf("2"), store.observeAll().first().map { it.id })
    }

    @Test
    fun `an empty feed response is recorded as initialized`() = runTest {
        val store = createInMemoryPostsLocalStore()
        assertFalse(store.observeFeedInitialized().first())

        store.replaceAll(emptyList())

        assertTrue(store.observeFeedInitialized().first())
    }

    @Test
    fun `a detail upsert does not claim the whole feed was initialized`() = runTest {
        val store = createInMemoryPostsLocalStore()

        store.upsert(entity("1"))

        assertFalse(store.observeFeedInitialized().first())
    }

    @Test
    fun `observeAll re-emits when the table is written`() = runTest {
        val store = createInMemoryPostsLocalStore()

        store.observeAll().test {
            assertEquals(emptyList(), awaitItem())

            store.replaceAll(listOf(entity("1")))

            assertEquals(listOf("1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeById emits null for a post that is not cached`() = runTest {
        val store = createInMemoryPostsLocalStore()

        assertNull(store.observeById("nope").first())
    }

    @Test
    fun `upsert of a cached post keeps its position`() = runTest {
        val store = createInMemoryPostsLocalStore()
        store.replaceAll(listOf(entity("1"), entity("2"), entity("3")))

        store.upsert(PostEntity("1", "changed", "changed body"))

        assertEquals(listOf("1", "2", "3"), store.observeAll().first().map { it.id })
        assertEquals("changed", store.observeById("1").first()?.title)
    }

    @Test
    fun `upsert of an unseen post appends it past the tail`() = runTest {
        val store = createInMemoryPostsLocalStore()
        store.replaceAll(listOf(entity("1"), entity("2")))

        store.upsert(entity("99"))

        assertEquals(listOf("1", "2", "99"), store.observeAll().first().map { it.id })
    }

    @Test
    fun `count reflects what is cached`() = runTest {
        val store = createInMemoryPostsLocalStore()
        assertEquals(0L, store.count())

        store.replaceAll(listOf(entity("1"), entity("2")))

        assertEquals(2L, store.count())
    }

    private fun entity(id: String) = PostEntity(id = id, title = "Title $id", body = "Body $id")
}
