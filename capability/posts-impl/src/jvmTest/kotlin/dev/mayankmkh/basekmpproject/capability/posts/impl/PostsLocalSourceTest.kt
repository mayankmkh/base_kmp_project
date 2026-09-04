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
    fun `replaceFeed round trips through the database in feed order`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.replaceFeed(listOf(entity("10"), entity("2")))

        // "10" before "2" -- the point of storing an explicit position rather than sorting on the
        // text id, which would put "10" second.
        assertEquals(listOf("10", "2"), source.observeFeed().first().map { it.id })
    }

    @Test
    fun `replaceFeed keeps the first occurrence of a duplicate id in feed order`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.replaceFeed(
            listOf(
                entity("1"),
                entity("2"),
                entity("1").copy(title = "Duplicate"),
                entity("3"),
            )
        )

        assertEquals(listOf("1", "2", "3"), source.observeFeed().first().map { it.id })
        assertEquals("Title 1", source.observeById("1").first()?.title)
    }

    @Test
    fun `smaller feed drops membership but retains the out of page entity`() = runTest {
        val source = createInMemoryPostsLocalSource()
        source.replaceFeed(listOf(entity("1"), entity("2")))

        source.replaceFeed(listOf(entity("2")))

        assertEquals(listOf("2"), source.observeFeed().first().map { it.id })
        assertEquals("Title 1", source.observeById("1").first()?.title)
        assertEquals(2L, source.count())
    }

    @Test
    fun `an empty feed response is recorded as initialized`() = runTest {
        val source = createInMemoryPostsLocalSource()
        assertFalse(source.observeFeedInitialized().first())

        source.replaceFeed(emptyList())

        assertTrue(source.observeFeedInitialized().first())
    }

    @Test
    fun `a detail upsert does not claim the whole feed was initialized`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.upsert(entity("1"))

        assertFalse(source.observeFeedInitialized().first())
    }

    @Test
    fun `observeFeed re-emits when membership is written`() = runTest {
        val source = createInMemoryPostsLocalSource()

        source.observeFeed().test {
            assertEquals(emptyList(), awaitItem())

            source.replaceFeed(listOf(entity("1")))

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
        source.replaceFeed(listOf(entity("1"), entity("2"), entity("3")))

        source.upsert(PostEntity("1", "changed", "changed body"))

        assertEquals(listOf("1", "2", "3"), source.observeFeed().first().map { it.id })
        assertEquals("changed", source.observeById("1").first()?.title)
    }

    @Test
    fun `upsert of an unseen post does not add feed membership`() = runTest {
        val source = createInMemoryPostsLocalSource()
        source.replaceFeed(listOf(entity("1"), entity("2")))

        source.upsert(entity("99"))

        assertEquals(listOf("1", "2"), source.observeFeed().first().map { it.id })
        assertEquals("Title 99", source.observeById("99").first()?.title)
    }

    @Test
    fun `count reflects what is cached`() = runTest {
        val source = createInMemoryPostsLocalSource()
        assertEquals(0L, source.count())

        source.replaceFeed(listOf(entity("1"), entity("2")))

        assertEquals(2L, source.count())
    }

    private fun entity(id: String) = PostEntity(id = id, title = "Title $id", body = "Body $id")
}
