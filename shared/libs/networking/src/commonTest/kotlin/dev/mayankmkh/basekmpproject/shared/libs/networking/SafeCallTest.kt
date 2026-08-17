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
    fun `maps a 500 onto ServerResponse with the parsed body`() = runTest {
        val client = client(HttpStatusCode.InternalServerError, """{"errors":"boom"}""")

        val error = assertIs<ApiError.ServerResponse>(client.call().getError())

        assertEquals("boom", error.serverError.errors)
    }

    private suspend fun HttpClient.call() = tryCatching {
        get("https://example.com/thing").bodyAsText()
    }

    /** `expectSuccess` is what turns a non-2xx response into the exception `tryCatching` maps. */
    private fun client(status: HttpStatusCode, body: String) =
        HttpClient(MockEngine { respond(body, status) }) { expectSuccess = true }
}
