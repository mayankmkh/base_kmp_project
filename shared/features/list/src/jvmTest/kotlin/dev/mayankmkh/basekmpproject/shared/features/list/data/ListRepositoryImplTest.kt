package dev.mayankmkh.basekmpproject.shared.features.list.data

import app.cash.turbine.test
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.libs.database.PostEntity
import dev.mayankmkh.basekmpproject.shared.libs.database.PostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.database.createInMemoryPostsLocalStore
import dev.mayankmkh.basekmpproject.shared.libs.networking.NetworkConfig
import dev.mayankmkh.basekmpproject.shared.libs.networking.createHttpClient
import dev.mayankmkh.basekmpproject.shared.libs.posts.PostsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The offline-first read, over a real Ktor stack and a real SQLDelight database.
 *
 * A `MockEngine` answers HTTP and `createInMemoryPostsLocalStore` supplies the cache, so what is
 * under test is the actual wiring -- decoding, the `shouldFetch` rule, the write, and the table
 * notification the read comes back on -- rather than a fake standing in for any of it.
 *
 * Lives in `jvmTest` because the in-memory JDBC driver does: the same tests would need a different
 * driver per platform, and this layer has no platform-specific behaviour to justify that.
 */
class ListRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `an empty cache is filled from the network and served in feed order`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson(FEED_JSON) }
            val store = createInMemoryPostsLocalStore()
            val repository = repository(engine, store)

            repository.getItems().test {
                // Store's validator treats the initial empty table as a cache miss.
                assertEquals(FEED_ITEMS, awaitItem().getOrThrow { it }.toList())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, engine.requestHistory.size)
            // Cached, so the next cold read has something to show offline.
            assertEquals(FEED_ITEMS.map { it.id }, store.observeAllOnce().map { it.id })
        }

    @Test
    fun `an empty server feed is emitted after the initial empty cache triggers a fetch`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson("[]") }

            repository(engine, createInMemoryPostsLocalStore()).getItems().test {
                assertTrue(awaitItem().getOrThrow { it }.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun `a populated cache is served without touching the network`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson(FEED_JSON) }
            val store = createInMemoryPostsLocalStore()
            store.replaceAll(listOf(PostEntity("7", "Cached", "Cached body")))

            repository(engine, store).getItems().test {
                assertEquals(
                    listOf(Item("7", "Cached", "Cached body")),
                    awaitItem().getOrThrow { it }.toList(),
                )
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun `a refresh replaces the feed and the open read sees it`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson(FEED_JSON) }
            val store = createInMemoryPostsLocalStore()
            store.replaceAll(listOf(PostEntity("7", "Cached", "Cached body")))
            val repository = repository(engine, store)

            repository.getItems().test {
                assertEquals(listOf("7"), awaitItem().getOrThrow { it }.map { it.id })

                repository.refresh()

                // The refreshed rows arrive through the same flow: the write is what the collector
                // is
                // watching, so nothing has to be handed back from `refresh`.
                assertEquals(FEED_ITEMS, awaitItem().getOrThrow { it }.toList())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a fetch that fails on an empty cache reports the failure`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            repository(engine, createInMemoryPostsLocalStore()).getItems().test {
                assertNotNull(awaitItem().getError())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a refresh that fails throws so the caller can announce it`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val store = createInMemoryPostsLocalStore()
            store.replaceAll(listOf(PostEntity("7", "Cached", "Cached body")))
            val repository = repository(engine, store)

            assertFailsWith<Throwable> { repository.refresh() }

            // The cache is untouched: a failed refresh must not empty the screen.
            assertEquals(listOf("7"), store.observeAllOnce().map { it.id })
        }

    private fun TestScope.repository(
        engine: MockEngine,
        store: PostsLocalStore,
    ) =
        ListRepositoryImpl(
            postsApi = PostsApi(createHttpClient(engine, config)),
            postsLocalStore = store,
            storeScope = backgroundScope,
        )

    private companion object {
        val config =
            NetworkConfig(
                baseUrl = Url("https://jsonplaceholder.typicode.com"),
                defaultHeaders = emptyMap(),
            )

        val FEED_ITEMS =
            listOf(Item("10", "Tenth", "Tenth body"), Item("2", "Second", "Second body"))

        /** Ids out of lexicographic order on purpose: `position` is what keeps the feed's own. */
        val FEED_JSON =
            """
            [
              {"userId": 1, "id": 10, "title": "Tenth", "body": "Tenth body"},
              {"userId": 1, "id": 2, "title": "Second", "body": "Second body"}
            ]
            """
                .trimIndent()

        fun MockRequestHandleScope.respondJson(body: String) =
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
    }
}

private suspend fun PostsLocalStore.observeAllOnce() = observeAll().first()
