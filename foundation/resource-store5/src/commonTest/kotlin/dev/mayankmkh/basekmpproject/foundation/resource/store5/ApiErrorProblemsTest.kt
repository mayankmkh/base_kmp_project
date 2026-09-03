package dev.mayankmkh.basekmpproject.foundation.resource.store5

import com.github.michaelbull.result.getError
import dev.mayankmkh.basekmpproject.foundation.network.ApiError
import dev.mayankmkh.basekmpproject.foundation.network.toApiError
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class ApiErrorProblemsTest {
    @Test
    fun `offline network errors map to retryable offline problems`() {
        val error = IOException("offline").toApiError()

        assertEquals(
            ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `timeout network errors map to retryable temporary problems`() {
        val error = HttpRequestTimeoutException(HttpRequestBuilder()).toApiError()

        assertEquals(
            ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `server responses map to retryable temporary problems`() = runTest {
        val error = apiError(HttpStatusCode.InternalServerError, """{"errors":"boom"}""")

        assertEquals(
            ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `unauthorized responses map to non-retryable access problems`() = runTest {
        val error = apiError(HttpStatusCode.Unauthorized, """{"message":"sign in"}""")

        assertEquals(
            ResourceProblem(ResourceProblemCategory.ACCESS, retryable = false),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `forbidden responses map to non-retryable access problems`() = runTest {
        val error = apiError(HttpStatusCode.Forbidden, """{"message":"nope"}""")

        assertEquals(
            ResourceProblem(ResourceProblemCategory.ACCESS, retryable = false),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `bad requests map to permanent problems`() = runTest {
        val error = apiError(HttpStatusCode.BadRequest, """{"errors":{"name":["bad"]}}""")

        assertEquals(
            ResourceProblem(ResourceProblemCategory.PERMANENT, retryable = false),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `other client responses map to permanent problems`() = runTest {
        val error = apiError(HttpStatusCode.NotFound, """{"message":"gone"}""")

        assertEquals(
            ResourceProblem(ResourceProblemCategory.PERMANENT, retryable = false),
            error.toResourceProblem(),
        )
    }

    @Test
    fun `unknown errors map to non-retryable unknown problems`() {
        val error = IllegalStateException("unexpected").toApiError()

        assertEquals(
            ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = false),
            error.toResourceProblem(),
        )
    }

    private suspend fun apiError(status: HttpStatusCode, body: String): ApiError {
        val client = HttpClient(MockEngine { respond(body, status) }) { expectSuccess = true }
        return requireNotNull(
            client.tryCatching { get("https://example.com/thing").bodyAsText() }.getError()
        )
    }
}
