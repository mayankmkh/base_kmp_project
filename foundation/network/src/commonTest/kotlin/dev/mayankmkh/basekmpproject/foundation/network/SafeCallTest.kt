package dev.mayankmkh.basekmpproject.foundation.network

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

class SafeCallTest {
    @Test
    fun `passes a successful body through`() = runTest {
        val client = client(HttpStatusCode.OK, "hello")

        assertEquals("hello", client.call().get())
    }

    @Test
    fun `maps 403 to Http and keeps its body readable`() = runTest {
        val client =
            client(
                HttpStatusCode.Forbidden,
                """{"message":"nope"}""",
                ContentType.Application.Json,
            )

        val error = assertIs<ApiError.Http>(client.call().getError())

        assertEquals(HttpStatusCode.Forbidden, error.status)
        assertEquals("nope", error.bodyOrNull<ErrorBody>()?.message)
    }

    @Test
    fun `maps 503 html to Http and optional decoding returns null`() = runTest {
        val client =
            client(
                HttpStatusCode.ServiceUnavailable,
                "<html>down</html>",
                ContentType.Text.Html,
            )

        val error = assertIs<ApiError.Http>(client.call().getError())

        assertEquals(HttpStatusCode.ServiceUnavailable, error.status)
        assertNull(error.bodyOrNull<ErrorBody>())
    }

    @Test
    fun `maps kotlinx io failures onto offline network errors`() = runTest {
        val client = HttpClient(MockEngine { throw IOException("offline") })

        val error = assertIs<ApiError.Network>(client.call().getError())

        assertEquals(NetworkFailureKind.OFFLINE, error.kind)
    }

    @Test
    fun `maps request timeout onto timeout network errors`() = runTest {
        val client =
            HttpClient(MockEngine { throw HttpRequestTimeoutException(HttpRequestBuilder()) })

        val error = assertIs<ApiError.Network>(client.call().getError())

        assertEquals(NetworkFailureKind.TIMEOUT, error.kind)
    }

    @Test
    fun `does not turn cancellation into an api error`() = runTest {
        val client = HttpClient(MockEngine { respond("unused") })

        assertFailsWith<CancellationException> {
            client.tryCatching<String> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `maps an unexpected exception onto Unknown`() = runTest {
        val client =
            HttpClient(MockEngine { throw IllegalStateException("no route to host") }) {
                expectSuccess = true
            }

        val error = assertIs<ApiError.Unknown>(client.call().getError())

        // Coroutines may rebuild an exception crossing a dispatch boundary to splice in the
        // caller's stack, so only its type and message are stable.
        assertIs<IllegalStateException>(error.cause)
        assertEquals("no route to host", error.cause.message)
    }

    private suspend fun HttpClient.call() = tryCatching {
        get("https://example.com/thing").bodyAsText()
    }

    /** `expectSuccess` turns a non-2xx response into the exception `tryCatching` maps. */
    private fun client(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType? = null,
    ): HttpClient {
        val headers =
            contentType?.let { headersOf(HttpHeaders.ContentType, it.toString()) } ?: headersOf()
        return createHttpClient(
            MockEngine { respond(body, status, headers) },
            NetworkConfig(Url("https://example.com")),
        )
    }

    @Serializable private data class ErrorBody(val message: String)
}
