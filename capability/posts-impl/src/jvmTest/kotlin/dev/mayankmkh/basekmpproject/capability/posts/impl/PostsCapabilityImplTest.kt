package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.capability.posts.api.PostFeed
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
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
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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

    @Test
    fun `reconnect without a feed observer defers the refresh to the next observer`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.replaceAll(listOf(PostEntity("7", "Cached", "Cached body", authorId = 70)))
            val online = MutableStateFlow(false)
            val engine = MockEngine { respondJson(FEED_JSON) }
            val capability = capability(engine, local, ConnectivityMonitor { online })

            online.value = true
            assertEquals(0, engine.requestHistory.size)

            // The deferred refresh may start before or after the cached row is read, so assert on
            // the sequence of distinct feed contents rather than on the interleaved operations.
            capability.observeFeed().postIds().test {
                assertEquals(listOf(7L), awaitItem())
                assertEquals(listOf(10L, 2L), awaitItem())
                assertEquals(1, engine.requestHistory.size)
                cancelAndIgnoreRemainingEvents()
            }

            // The debt is settled once: a second observer inherits the fresh feed without a fetch.
            capability.observeFeed().postIds().test {
                assertEquals(listOf(10L, 2L), awaitItem())
                assertEquals(1, engine.requestHistory.size)
                cancelAndIgnoreRemainingEvents()
            }

            capability.close()
        }

    @Test
    fun `a background refresh after an unobserved reconnect settles the deferred refresh`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.replaceAll(listOf(PostEntity("7", "Cached", "Cached body", authorId = 70)))
            val online = MutableStateFlow(false)
            val engine = MockEngine { respondJson(FEED_JSON) }
            val capability = capability(engine, local, ConnectivityMonitor { online })

            online.value = true
            assertIs<RefreshOutcome.Succeeded>(capability.refreshFeed(RefreshQos.background()))
            assertEquals(1, engine.requestHistory.size)

            capability.observeFeed().postIds().test {
                assertEquals(listOf(10L, 2L), awaitItem())
                assertEquals(1, engine.requestHistory.size)
                cancelAndIgnoreRemainingEvents()
            }

            capability.close()
        }

    @Test
    fun `post observers share one resource and release it after the last collector`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            local.upsert(PostEntity("1", "Cached", "Cached body", authorId = 1))
            val engine = MockEngine { respondJson(POST_JSON) }
            val capability = capability(engine, local)
            val id = PostId(1)

            val first = launch { capability.observePost(id).collect() }
            val second = launch { capability.observePost(id).collect() }
            assertEquals(1, capability.postResourceCountForTest())

            first.cancelAndJoin()
            assertEquals(1, capability.postResourceCountForTest())
            second.cancelAndJoin()
            assertEquals(0, capability.postResourceCountForTest())

            capability.close()
        }

    @Test
    fun `refreshing an unobserved post does not retain its resource`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            val engine = MockEngine { respondJson(POST_JSON) }
            val capability = capability(engine, local)

            assertSame(RefreshOutcome.Succeeded, capability.refreshPost(PostId(1)))

            assertEquals(0, capability.postResourceCountForTest())
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

private fun Flow<ResourceObservation<PostFeed>>.postIds(): Flow<List<Long>> =
    map { observation -> observation.value?.posts?.map { post -> post.id.value } }
        .filterNotNull()
        .distinctUntilChanged()
