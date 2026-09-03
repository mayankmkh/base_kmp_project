package dev.mayankmkh.basekmpproject.foundation.network

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

class SafeCallTest {
    @Test
    fun `passes a successful body through`() = runTest {
        val client = responseClient(HttpStatusCode.OK, "hello")

        assertEquals("hello", client.textCall().get())
    }

    @Test
    fun `maps 4xx to Http with copied metadata and a decodable body`() = runTest {
        val client =
            responseClient(
                HttpStatusCode.Forbidden,
                """{"message":"nope"}""",
                ContentType.Application.Json,
                headersOf("X-Server", "edge"),
            )

        val error = assertIs<NetworkFailure.Http>(client.textCall().getError())

        assertEquals(HttpStatusCode.Forbidden, error.status)
        assertEquals("edge", error.headers["X-Server"])
        assertContentEquals("""{"message":"nope"}""".encodeToByteArray(), error.body)
        assertNotNull(error.requestId)
        assertEquals("nope", error.bodyOrNull<ErrorBody>(createJson())?.message)
    }

    @Test
    fun `maps an html 503 to Http and optional body decoding returns null`() = runTest {
        val client =
            responseClient(
                HttpStatusCode.ServiceUnavailable,
                "<html>down</html>",
                ContentType.Text.Html,
            )

        val error = assertIs<NetworkFailure.Http>(client.textCall().getError())

        assertEquals(HttpStatusCode.ServiceUnavailable, error.status)
        assertNull(error.bodyOrNull<ErrorBody>(createJson()))
    }

    @Test
    fun `maps malformed successful json to Decoding`() = runTest {
        val client = responseClient(HttpStatusCode.OK, "{broken", ContentType.Application.Json)

        val error = client.tryCatching { get("thing").body<ErrorBody>() }.getError()

        assertIs<NetworkFailure.Decoding>(error)
    }

    @Test
    fun `maps missing transformations to Decoding`() = runTest {
        val client = responseClient(HttpStatusCode.OK, "body", ContentType.Text.Plain)

        val error = client.tryCatching { get("thing").body<UnsupportedBody>() }.getError()

        assertIs<NetworkFailure.Decoding>(error)
    }

    @Test
    fun `maps io failure to offline transport`() = runTest {
        val client = HttpClient(MockEngine { throw IOException("offline") })

        val error = assertIs<NetworkFailure.Transport>(client.textCall().getError())

        assertEquals(TransportFailureKind.OFFLINE, error.kind)
    }

    @Test
    fun `unwraps cancellation-wrapped request timeout into timeout transport`() = runTest {
        val timeout = HttpRequestTimeoutException(HttpRequestBuilder())
        val client = HttpClient(MockEngine { throw CancellationException("wrapped", timeout) })

        val error = assertIs<NetworkFailure.Transport>(client.textCall().getError())

        assertEquals(TransportFailureKind.TIMEOUT, error.kind)
        assertIs<HttpRequestTimeoutException>(error.cause)
    }

    @Test
    fun `rethrows genuine cancellation`() = runTest {
        val client = HttpClient(MockEngine { respond("unused") })

        assertFailsWith<CancellationException> {
            client.tryCatching<String> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `maps an unexpected exception to Unexpected`() = runTest {
        val client = HttpClient(MockEngine { throw IllegalStateException("unexpected") })

        val error = assertIs<NetworkFailure.Unexpected>(client.textCall().getError())

        assertIs<IllegalStateException>(error.cause)
        assertEquals("unexpected", error.cause.message)
    }

    private suspend fun HttpClient.textCall() = tryCatching {
        get("https://example.com/thing").bodyAsText()
    }

    private fun responseClient(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType? = null,
        extraHeaders: io.ktor.http.Headers = headersOf(),
    ): HttpClient {
        val headers =
            io.ktor.http.headers {
                appendAll(extraHeaders)
                if (contentType != null) append(HttpHeaders.ContentType, contentType.toString())
            }
        return createHttpClient(
            MockEngine { respond(body, status, headers) },
            NetworkConfig(Url("https://example.com")),
        )
    }

    @Serializable private data class ErrorBody(val message: String)

    private class UnsupportedBody
}
