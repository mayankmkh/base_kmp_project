package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class PostsCapabilityImplTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `post paths merge with the configured base url`() = runTest {
        val engine = MockEngine { respondJson(POST_JSON) }

        val post = PostsRemoteSource(createHttpClient(engine, CONFIG)).getPost(1).component1()

        assertNotNull(post)
        assertEquals(
            "https://jsonplaceholder.typicode.com/posts/1",
            engine.requestHistory.single().url.toString(),
        )
    }

    @Test
    fun `cached feed stays stale until refresh completes fresh`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.replaceAll(listOf(PostEntity("7", "Cached", "Cached body", authorId = 70)))
            val engine = MockEngine { respondJson(FEED_JSON) }
            val capability = capability(engine, local)

            capability
                .observeFeed()
                .filter { it.value != null }
                .test {
                    val cached = awaitItem()
                    assertEquals(ResourceFreshness.STALE, cached.freshness)
                    assertEquals(listOf(7L), cached.value?.posts?.map { it.id.value })

                    launch { capability.refreshFeed() }
                    assertIs<ResourceOperation.Refreshing>(awaitItem().operation)
                    val fresh = awaitItem()
                    assertEquals(ResourceFreshness.FRESH, fresh.freshness)
                    assertEquals(listOf(10L, 2L), fresh.value?.posts?.map { it.id.value })
                    cancelAndIgnoreRemainingEvents()
                }

            capability.close()
        }

    @Test
    fun `offline refresh keeps cached value stale and exposes retryable failure`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.replaceAll(listOf(PostEntity("7", "Cached", "Cached body", authorId = 70)))
            val engine = MockEngine { throw IOException("offline") }
            val capability = capability(engine, local)

            capability
                .observeFeed()
                .filter { it.value != null }
                .test {
                    assertEquals(ResourceFreshness.STALE, awaitItem().freshness)
                    launch { capability.refreshFeed() }
                    assertIs<ResourceOperation.Refreshing>(awaitItem().operation)
                    val failed = awaitItem()

                    assertEquals(ResourceFreshness.STALE, failed.freshness)
                    assertEquals(listOf(7L), failed.value?.posts?.map { it.id.value })
                    val operation = assertIs<ResourceOperation.Failed>(failed.operation)
                    assertEquals(ResourceProblemCategory.OFFLINE, operation.problem.category)
                    assertEquals(true, operation.problem.retryable)
                    cancelAndIgnoreRemainingEvents()
                }

            capability.close()
        }

    @Test
    fun `reconnect refreshes the feed in the capability child scope`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.replaceAll(listOf(PostEntity("7", "Cached", "Cached body", authorId = 70)))
            val online = MutableStateFlow(false)
            val engine = MockEngine { respondJson(FEED_JSON) }
            val capability = capability(engine, local, ConnectivityMonitor { online })

            capability
                .observeFeed()
                .filter { it.value != null }
                .test {
                    assertEquals(listOf(7L), awaitItem().value?.posts?.map { it.id.value })
                    online.value = true

                    assertIs<ResourceOperation.Refreshing>(awaitItem().operation)
                    val refreshed = awaitItem()
                    assertEquals(listOf(10L, 2L), refreshed.value?.posts?.map { it.id.value })
                    assertEquals(1, engine.requestHistory.size)
                    cancelAndIgnoreRemainingEvents()
                }

            capability.close()
        }

    /**
     * A ViewModel cleared mid-fetch cancels the coroutine that called `refreshFeed`. The
     * `Refreshing` it published is shared by every other observer of the feed, so the fetch has to
     * outlive its caller and publish a terminal state; otherwise the remaining observers wait on a
     * refresh nobody is still running.
     */
    @Test
    fun `cancelling the calling coroutine still settles the refresh`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.replaceAll(listOf(PostEntity("7", "Cached", "Cached body", authorId = 70)))
            val released = CompletableDeferred<Unit>()
            val engine = MockEngine {
                released.await()
                respondJson(FEED_JSON)
            }
            val capability = capability(engine, local)

            capability
                .observeFeed()
                .filter { it.value != null }
                .test {
                    assertEquals(ResourceFreshness.STALE, awaitItem().freshness)

                    val caller = launch { capability.refreshFeed() }
                    assertIs<ResourceOperation.Refreshing>(awaitItem().operation)
                    caller.cancel()
                    released.complete(Unit)

                    var settled = awaitItem()
                    while (settled.operation is ResourceOperation.Refreshing) {
                        settled = awaitItem()
                    }
                    assertEquals(ResourceOperation.Idle, settled.operation)
                    assertEquals(ResourceFreshness.FRESH, settled.freshness)
                    assertEquals(listOf(10L, 2L), settled.value?.posts?.map { it.id.value })
                    cancelAndIgnoreRemainingEvents()
                }

            capability.close()
        }

    private fun capability(
        engine: MockEngine,
        local: PostsLocalSource,
        connectivity: ConnectivityMonitor = ConnectivityMonitor { MutableStateFlow(true) },
    ): PostsCapabilityImpl {
        val runtime =
            ApplicationRuntimeScope(
                dispatcher,
                CoroutineExceptionHandler { _, throwable -> throw throwable },
            )
        return PostsCapabilityImpl(
            remoteSource = PostsRemoteSource(createHttpClient(engine, CONFIG)),
            localSource = local,
            applicationRuntimeScope = runtime,
            connectivityMonitor = connectivity,
        )
    }

    private companion object {
        val CONFIG = NetworkConfig(baseUrl = Url("https://jsonplaceholder.typicode.com"))

        const val POST_JSON = """{"userId":1,"id":1,"title":"First","body":"First body"}"""

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
