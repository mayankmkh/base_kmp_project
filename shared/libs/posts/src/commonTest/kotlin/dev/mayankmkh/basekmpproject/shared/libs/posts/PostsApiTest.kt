package dev.mayankmkh.basekmpproject.shared.libs.posts

import com.github.michaelbull.result.getError
import dev.mayankmkh.basekmpproject.shared.libs.networking.ApiError
import dev.mayankmkh.basekmpproject.shared.libs.networking.NetworkConfig
import dev.mayankmkh.basekmpproject.shared.libs.networking.createHttpClient
import dev.mayankmkh.basekmpproject.shared.libs.networking.getOrThrow
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PostsApiTest {

    @Test
    fun `getPosts asks for a limited feed and decodes it`() = runTest {
        val engine = MockEngine { respondJson(FEED_JSON) }

        val posts = api(engine).getPosts().getOrThrow()

        assertEquals(
            "https://jsonplaceholder.typicode.com/posts?_limit=30",
            engine.requestHistory.single().url.toString(),
        )
        assertEquals(listOf(1, 2), posts.map { it.id })
        assertEquals("first", posts.first().title)
        assertEquals("first body", posts.first().body)
    }

    @Test
    fun `getPosts passes a caller's limit on to the query`() = runTest {
        val engine = MockEngine { respondJson("[]") }

        api(engine).getPosts(limit = 5)

        assertEquals("5", engine.requestHistory.single().url.parameters["_limit"])
    }

    @Test
    fun `getPost fetches one post by id`() = runTest {
        val engine = MockEngine { respondJson(SINGLE_JSON) }

        val post = api(engine).getPost("2").getOrThrow()

        assertEquals(
            "https://jsonplaceholder.typicode.com/posts/2",
            engine.requestHistory.single().url.toString(),
        )
        assertEquals(PostDto(userId = 1, id = 2, title = "second", body = "second body"), post)
    }

    @Test
    fun `no call carries an authorization header`() = runTest {
        // The sample endpoint is open; leaving auth on would put a refresh in front of every read.
        val engine = MockEngine { respondJson(SINGLE_JSON) }
        val api = api(engine)

        api.getPosts()
        api.getPost("2")

        assertEquals(2, engine.requestHistory.size)
        engine.requestHistory.forEach { assertNull(it.headers[HttpHeaders.Authorization]) }
    }

    @Test
    fun `a rejected call comes back as a failure rather than an exception`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }

        val error = api(engine).getPost("999").getError()

        // The body is Ktor's plain-text default, not the `ClientError` shape the mapper wants, so
        // it lands on the documented fallback instead of blowing up inside the mapper.
        assertIs<ApiError.Unknown>(error)
    }

    @Test
    fun `getOrThrow rethrows the cause a network bound resource is watching for`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val error = api(engine).getPost("999").getError()

        val thrown = assertFailsWith<Throwable> { api(engine).getPost("999").getOrThrow() }

        assertEquals(error?.throwable?.message, thrown.message)
    }

    private fun api(engine: MockEngine) = PostsApi(createHttpClient(engine, config))

    private companion object {
        val config =
            NetworkConfig(
                baseUrl = Url("https://jsonplaceholder.typicode.com"),
                defaultHeaders = emptyMap(),
            )

        val FEED_JSON =
            """
            [
              {"userId": 1, "id": 1, "title": "first", "body": "first body"},
              {"userId": 1, "id": 2, "title": "second", "body": "second body"}
            ]
            """
                .trimIndent()

        const val SINGLE_JSON =
            """{"userId": 1, "id": 2, "title": "second", "body": "second body"}"""

        fun MockRequestHandleScope.respondJson(body: String) =
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
    }
}
