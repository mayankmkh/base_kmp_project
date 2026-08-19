package dev.mayankmkh.basekmpproject.shared.libs.networking

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException

class SafeCallTest {
    @Test
    fun `passes a successful body through`() = runTest {
        val client = client(HttpStatusCode.OK, "hello")

        assertEquals("hello", client.call().get())
    }

    @Test
    fun `maps a 403 onto Forbidden with the parsed body`() = runTest {
        val client = client(HttpStatusCode.Forbidden, """{"message":"nope"}""")

        val error = assertIs<ApiError.ClientRequest.Forbidden>(client.call().getError())

        assertEquals("nope", error.clientError.message)
    }

    @Test
    fun `maps a 400 onto BadRequest with the errors keyed by field`() = runTest {
        val client = client(HttpStatusCode.BadRequest, """{"errors":{"name":["too short"]}}""")

        val error = assertIs<ApiError.ClientRequest.BadRequest>(client.call().getError())

        assertEquals(mapOf("name" to listOf("too short")), error.errors.errors)
    }

    @Test
    fun `maps any other 4xx onto Other`() = runTest {
        val client = client(HttpStatusCode.NotFound, """{"message":"gone"}""")

        val error = assertIs<ApiError.ClientRequest.Other>(client.call().getError())

        assertEquals("gone", error.clientError.message)
    }

    @Test
    fun `maps a 500 onto ServerResponse with the parsed body`() = runTest {
        val client = client(HttpStatusCode.InternalServerError, """{"errors":"boom"}""")

        val error = assertIs<ApiError.ServerResponse>(client.call().getError())

        assertEquals("boom", error.serverError.errors)
    }

    @Test
    fun `falls back to Unknown when the error body is not the shape it claims`() = runTest {
        val client = client(HttpStatusCode.Forbidden, "<html>go away</html>")

        val error = assertIs<ApiError.Unknown>(client.call().getError())

        assertIs<SerializationException>(error.throwable)
    }

    @Test
    fun `falls back to Unknown when nothing came back at all`() = runTest {
        val client =
            HttpClient(MockEngine { throw IllegalStateException("no route to host") }) {
                expectSuccess = true
            }

        val error = assertIs<ApiError.Unknown>(client.call().getError())

        // Not the same instance: coroutines rebuilds an exception crossing a dispatch boundary to
        // splice in the caller's stack, so only the type and message survive.
        assertIs<IllegalStateException>(error.throwable)
        assertEquals("no route to host", error.throwable.message)
    }

    private suspend fun HttpClient.call() = tryCatching {
        get("https://example.com/thing").bodyAsText()
    }

    /** `expectSuccess` is what turns a non-2xx response into the exception `tryCatching` maps. */
    private fun client(status: HttpStatusCode, body: String) =
        HttpClient(MockEngine { respond(body, status) }) { expectSuccess = true }
}
