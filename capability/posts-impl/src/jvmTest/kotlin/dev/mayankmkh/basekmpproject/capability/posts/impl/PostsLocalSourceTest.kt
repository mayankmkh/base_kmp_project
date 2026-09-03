package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PostsLocalSourceTest {

    @Test
    fun `replaceAll round trips through the database in feed order`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.replaceAll(listOf(entity("10"), entity("2")))

        // "10" before "2" -- the point of storing an explicit position rather than sorting on the
        // text id, which would put "10" second.
        assertEquals(listOf("10", "2"), source.observeAll().first().map { it.id })
    }

    @Test
    fun `replaceAll drops rows the new feed no longer contains`() = runTest {
        val source = createInMemoryPostsLocalSource()
        source.replaceAll(listOf(entity("1"), entity("2")))

        source.replaceAll(listOf(entity("2")))

        assertEquals(listOf("2"), source.observeAll().first().map { it.id })
    }

    @Test
    fun `an empty feed response is recorded as initialized`() = runTest {
        val source = createInMemoryPostsLocalSource()
        assertFalse(source.observeFeedInitialized().first())

        source.replaceAll(emptyList())

        assertTrue(source.observeFeedInitialized().first())
    }

    @Test
    fun `a detail upsert does not claim the whole feed was initialized`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.upsert(entity("1"))

        assertFalse(source.observeFeedInitialized().first())
    }

    @Test
    fun `observeAll re-emits when the table is written`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.observeAll().test {
            assertEquals(emptyList(), awaitItem())

            source.replaceAll(listOf(entity("1")))

            assertEquals(listOf("1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeById emits null for a post that is not cached`() = runTest {
        val source = createInMemoryPostsLocalSource()

        assertNull(source.observeById("nope").first())
    }

    @Test
    fun `upsert of a cached post keeps its position`() = runTest {
        val source = createInMemoryPostsLocalSource()
        source.replaceAll(listOf(entity("1"), entity("2"), entity("3")))

        source.upsert(PostEntity("1", "changed", "changed body"))

        assertEquals(listOf("1", "2", "3"), source.observeAll().first().map { it.id })
        assertEquals("changed", source.observeById("1").first()?.title)
    }

    @Test
    fun `upsert of an unseen post appends it past the tail`() = runTest {
        val source = createInMemoryPostsLocalSource()
        source.replaceAll(listOf(entity("1"), entity("2")))

        source.upsert(entity("99"))

        assertEquals(listOf("1", "2", "99"), source.observeAll().first().map { it.id })
    }

    @Test
    fun `count reflects what is cached`() = runTest {
        val source = createInMemoryPostsLocalSource()
        assertEquals(0L, source.count())

        source.replaceAll(listOf(entity("1"), entity("2")))

        assertEquals(2L, source.count())
    }

    private fun entity(id: String) = PostEntity(id = id, title = "Title $id", body = "Body $id")
}
