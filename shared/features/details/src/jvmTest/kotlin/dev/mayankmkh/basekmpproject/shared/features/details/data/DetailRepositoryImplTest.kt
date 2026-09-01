package dev.mayankmkh.basekmpproject.shared.features.details.data

import app.cash.turbine.test
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.testing.testDispatchers
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The single-item read, over a real Ktor stack and a real SQLDelight database.
 *
 * The first test here is the one that matters most: opening details for a post the app has never
 * listed used to be a crash, because the screen read the item out of whatever the list had last
 * saved. It now fetches.
 */
class DetailRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `a post that was never listed is fetched and cached`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson(POST_JSON) }
            val store = createInMemoryPostsLocalStore()

            repository(engine, store).getItem("2").test {
                assertEquals(POST_ITEM, awaitItem().getOrThrow { it })
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                "https://jsonplaceholder.typicode.com/posts/2",
                engine.requestHistory.single().url.toString(),
            )
            assertEquals(listOf("2"), store.observeAll().first().map { it.id })
        }

    @Test
    fun `a cached post is served without touching the network`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson(POST_JSON) }
            val store = createInMemoryPostsLocalStore()
            store.replaceAll(listOf(PostEntity("2", "Cached", "Cached body")))

            repository(engine, store).getItem("2").test {
                assertEquals(Item("2", "Cached", "Cached body"), awaitItem().getOrThrow { it })
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun `caching the fetched post leaves the rest of the feed alone`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondJson(POST_JSON) }
            val store = createInMemoryPostsLocalStore()
            store.replaceAll(
                listOf(
                    PostEntity("7", "Seventh", "Seventh body"),
                    PostEntity("8", "Eighth", "Eighth"),
                )
            )

            repository(engine, store).getItem("2").test {
                assertEquals(POST_ITEM, awaitItem().getOrThrow { it })
                cancelAndIgnoreRemainingEvents()
            }

            // Appended, not substituted: this read knows about one post, and evicting the feed
            // would
            // empty the list screen behind it.
            assertEquals(listOf("7", "8", "2"), store.observeAll().first().map { it.id })
        }

    @Test
    fun `a post the server does not have comes back as a failure`() =
        runTest(dispatcher) {
            val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
            val failures = RecordingFetchFailureListener()

            repository(engine, createInMemoryPostsLocalStore(), failures).getItem("999").test {
                assertNotNull(awaitItem().getError())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, failures.failures.size)
        }

    private fun repository(
        engine: MockEngine,
        store: PostsLocalStore,
        failureListener: NetworkBoundResource.OnFailureListener = RecordingFetchFailureListener(),
    ) =
        DetailRepositoryImpl(
            postsApi = PostsApi(createHttpClient(engine, config)),
            postsLocalStore = store,
            appDispatchers = testDispatchers(dispatcher),
            failureListener = failureListener,
        )

    private class RecordingFetchFailureListener : NetworkBoundResource.OnFailureListener {
        val failures = mutableListOf<Throwable>()

        override fun onFetchFailed(throwable: Throwable) {
            failures += throwable
        }
    }

    private companion object {
        val config =
            NetworkConfig(
                baseUrl = Url("https://jsonplaceholder.typicode.com"),
                defaultHeaders = emptyMap(),
            )

        val POST_ITEM = Item("2", "Second", "Second body")

        const val POST_JSON = """{"userId": 1, "id": 2, "title": "Second", "body": "Second body"}"""

        fun MockRequestHandleScope.respondJson(body: String) =
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
    }
}
