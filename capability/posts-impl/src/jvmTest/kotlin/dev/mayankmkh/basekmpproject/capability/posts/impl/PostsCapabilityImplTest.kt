package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

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
    fun `smaller feed keeps an out of page row and its detail observation`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            val engine = mockEngine { request ->
                if (request.url.encodedPath.endsWith("/99")) respondJson(DETAIL_99_JSON)
                else respondJson(FEED_JSON)
            }
            val capability = capability(engine, local)
            assertSame(RefreshOutcome.Succeeded, capability.refreshPost(PostId(99)))

            capability.observePost(PostId(99)).test {
                val detail = awaitItem()
                assertEquals("Outside page", detail.value?.title)
                assertSame(ResourceOperation.Idle, detail.operation)

                assertSame(RefreshOutcome.Succeeded, capability.refreshFeed())
                assertEquals("Outside page", local.observeById("99").first()?.title)
                assertEquals(listOf("10", "2"), local.observeFeed().first().map { it.id })
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `offline cold start stays valueless from loading through failure`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            val release = CompletableDeferred<Unit>()
            val engine = mockEngine {
                release.await()
                throw IOException("offline")
            }
            val capability = capability(engine, local)

            capability.observeFeed().test {
                val loading = awaitItem()
                assertEquals(null, loading.value)
                assertSame(ResourceOperation.Refreshing, loading.operation)

                release.complete(Unit)
                val failed = awaitItem()
                assertEquals(null, failed.value)
                assertEquals(
                    ResourceProblemCategory.OFFLINE,
                    assertIs<ResourceOperation.Failed>(failed.operation).problem.category,
                )
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `a synchronized empty feed is an idle value`() =
        runTest(dispatcher) {
            val local = createInMemoryPostsLocalSource()
            val engine = mockEngine { respondJson("[]") }
            val capability = capability(engine, local)
            assertSame(RefreshOutcome.Succeeded, capability.refreshFeed())

            capability.observeFeed().test {
                val observation = awaitItem()
                assertEquals(emptyList(), observation.value?.posts)
                assertSame(ResourceOperation.Idle, observation.operation)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            capability.close()
        }

    @Test
    fun `detail and feed syncs use atomic last commit wins rows`() =
        runTest(dispatcher) {
            val detailStarted = CompletableDeferred<Unit>()
            val feedStarted = CompletableDeferred<Unit>()
            val releaseDetail = CompletableDeferred<Unit>()
            val releaseFeed = CompletableDeferred<Unit>()
            val engine = mockEngine { request ->
                if (request.url.encodedPath.endsWith("/10")) {
                    detailStarted.complete(Unit)
                    releaseDetail.await()
                    respondJson(DETAIL_JSON)
                } else {
                    feedStarted.complete(Unit)
                    releaseFeed.await()
                    respondJson(FEED_JSON)
                }
            }
            val local = createInMemoryPostsLocalSource()
            val capability = capability(engine, local)
            val detail = backgroundScope.launch { capability.refreshPost(PostId(10)) }
            val feed = backgroundScope.launch { capability.refreshFeed() }
            detailStarted.await()
            feedStarted.await()

            releaseDetail.complete(Unit)
            detail.join()
            assertEquals("Detail", local.observeById("10").first()?.title)
            releaseFeed.complete(Unit)
            feed.join()

            assertEquals("Tenth", local.observeById("10").first()?.title)
            assertEquals("Tenth", local.observeFeed().first().first().title)
            assertEquals(2, local.observeFeed().first().size)
            capability.close()
        }

    @Test
    fun `reconnect retries only observed keys that failed offline`() =
        runTest(dispatcher) {
            val online = MutableStateFlow(false)
            // The mock engine only records answered requests, so count every attempt here.
            val attempts = mutableListOf<String>()
            // Post 1 is reachable throughout; the feed and post 2 fail offline until reconnect.
            val engine = mockEngine { request ->
                attempts += request.url.encodedPath
                when {
                    request.url.encodedPath.endsWith("/1") -> respondJson(POST_ONE_JSON)
                    !online.value -> throw IOException("offline")
                    request.url.encodedPath.endsWith("/2") -> respondJson(POST_TWO_JSON)
                    else -> respondJson(FEED_JSON)
                }
            }
            val capability =
                capability(
                    engine,
                    createInMemoryPostsLocalSource(),
                    ConnectivityMonitor { online },
                )
            val feedObserver = backgroundScope.launch { capability.observeFeed().collect() }
            val postObserver = backgroundScope.launch {
                capability.observePost(PostId(1)).collect()
            }
            runCurrent()
            val unobservedFailure =
                assertIs<RefreshOutcome.Failed>(
                    capability.refreshPost(PostId(2), RefreshQos.visible())
                )
            assertEquals(ResourceProblemCategory.OFFLINE, unobservedFailure.problem.category)
            assertEquals(mapOf("feed" to 1, "1" to 1, "2" to 1), attempts.pathCounts())

            online.value = true
            runCurrent()

            // The observed feed failed offline and is retried; the observed post succeeded and the
            // unobserved post is nobody's concern until it is observed or refreshed again.
            assertEquals(mapOf("feed" to 2, "1" to 1, "2" to 1), attempts.pathCounts())
            assertEquals(2, capability.observeFeed().first().value?.posts?.size)
            feedObserver.cancelAndJoin()
            postObserver.cancelAndJoin()
            capability.close()
        }

    @Test
    fun `re-observing within the interval does not start another sync`() =
        runTest(dispatcher) {
            val engine = mockEngine { respondJson(FEED_JSON) }
            val capability = capability(engine, createInMemoryPostsLocalSource())

            repeat(3) {
                val observer = backgroundScope.launch { capability.observeFeed().collect() }
                runCurrent()
                observer.cancelAndJoin()
            }

            assertEquals(mapOf("feed" to 1), engine.requestCounts())
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

    /** Runs the mock transport on the test dispatcher so every sync completes inside runCurrent. */
    private fun mockEngine(handler: MockRequestHandler): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = this@PostsCapabilityImplTest.dispatcher
                addHandler(handler)
            }
        )

    private fun MockEngine.requestCounts(): Map<String, Int> =
        requestHistory.map { it.url.encodedPath }.pathCounts()

    private fun List<String>.pathCounts(): Map<String, Int> =
        groupingBy { path ->
                path.substringAfterLast("/").takeIf { it.all(Char::isDigit) } ?: "feed"
            }
            .eachCount()

    private companion object {
        val CONFIG = NetworkConfig(baseUrl = Url("https://jsonplaceholder.typicode.com"))

        const val POST_JSON = """{"userId":1,"id":1,"title":"First","body":"First body"}"""
        const val POST_ONE_JSON = """{"userId":1,"id":1,"title":"One","body":"One body"}"""
        const val POST_TWO_JSON = """{"userId":1,"id":2,"title":"Two","body":"Two body"}"""
        const val DETAIL_JSON = """{"userId":1,"id":10,"title":"Detail","body":"Detail body"}"""
        const val DETAIL_99_JSON =
            """{"userId":1,"id":99,"title":"Outside page","body":"Outside body"}"""

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
