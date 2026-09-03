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

        assertProblem(ResourceProblemCategory.OFFLINE, retryable = true, error)
    }

    @Test
    fun `timeout network errors map to retryable temporary problems`() {
        val error = HttpRequestTimeoutException(HttpRequestBuilder()).toApiError()

        assertProblem(ResourceProblemCategory.TEMPORARY, retryable = true, error)
    }

    @Test
    fun `503 html responses map to retryable temporary problems`() = runTest {
        val error = apiError(HttpStatusCode.ServiceUnavailable, "<html>down</html>")

        assertProblem(ResourceProblemCategory.TEMPORARY, retryable = true, error)
    }

    @Test
    fun `429 responses map to retryable temporary problems`() = runTest {
        val error = apiError(HttpStatusCode.TooManyRequests, "slow down")

        assertProblem(ResourceProblemCategory.TEMPORARY, retryable = true, error)
    }

    @Test
    fun `request timeout responses map to retryable temporary problems`() = runTest {
        val error = apiError(HttpStatusCode.RequestTimeout, "timed out")

        assertProblem(ResourceProblemCategory.TEMPORARY, retryable = true, error)
    }

    @Test
    fun `unauthorized responses map to non-retryable access problems`() = runTest {
        val error = apiError(HttpStatusCode.Unauthorized, "sign in")

        assertProblem(ResourceProblemCategory.ACCESS, retryable = false, error)
    }

    @Test
    fun `forbidden responses map to non-retryable access problems`() = runTest {
        val error = apiError(HttpStatusCode.Forbidden, "nope")

        assertProblem(ResourceProblemCategory.ACCESS, retryable = false, error)
    }

    @Test
    fun `other client responses map to permanent problems`() = runTest {
        val error = apiError(HttpStatusCode.NotFound, "gone")

        assertProblem(ResourceProblemCategory.PERMANENT, retryable = false, error)
    }

    @Test
    fun `redirect responses map to unknown problems`() = runTest {
        val error = apiError(HttpStatusCode.NotModified, "not modified")

        assertProblem(ResourceProblemCategory.UNKNOWN, retryable = false, error)
    }

    @Test
    fun `unknown errors map to non-retryable unknown problems`() {
        val error = IllegalStateException("unexpected").toApiError()

        assertProblem(ResourceProblemCategory.UNKNOWN, retryable = false, error)
    }

    private fun assertProblem(
        category: ResourceProblemCategory,
        retryable: Boolean,
        error: ApiError,
    ) {
        assertEquals(ResourceProblem(category, retryable), error.toResourceProblem())
    }

    private suspend fun apiError(status: HttpStatusCode, body: String): ApiError {
        val client = HttpClient(MockEngine { respond(body, status) }) { expectSuccess = true }
        return requireNotNull(
            client.tryCatching { get("https://example.com/thing").bodyAsText() }.getError()
        )
    }
}
