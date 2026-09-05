package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.TransportFailureKind
import dev.mayankmkh.basekmpproject.foundation.network.toNetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
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
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class NetworkFailureProblemsTest {
    @Test
    fun `commit persists a success once and reports success`() = runTest {
        val result: Result<String, NetworkFailure> = Ok("value")
        val persisted = mutableListOf<String>()

        val outcome = result.commit(persisted::add)

        assertSame(RefreshOutcome.Succeeded, outcome)
        assertEquals(listOf("value"), persisted)
    }

    @Test
    fun `commit maps an offline failure without persisting`() = runTest {
        val failure =
            NetworkFailure.Transport(
                kind = TransportFailureKind.OFFLINE,
                cause = IOException("offline"),
            )
        val result: Result<String, NetworkFailure> = Err(failure)
        var persistCalls = 0

        val outcome = result.commit { persistCalls += 1 }

        assertEquals(0, persistCalls)
        assertEquals(
            ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true),
            assertIs<RefreshOutcome.Failed>(outcome).problem,
        )
    }

    @Test
    fun `commit maps a 404 failure without persisting`() = runTest {
        val result: Result<String, NetworkFailure> =
            Err(networkFailure(HttpStatusCode.NotFound, "gone"))
        var persistCalls = 0

        val outcome = result.commit { persistCalls += 1 }

        assertEquals(0, persistCalls)
        assertEquals(
            ResourceProblem(ResourceProblemCategory.PERMANENT, retryable = false),
            assertIs<RefreshOutcome.Failed>(outcome).problem,
        )
    }

    @Test
    fun `offline transport failures map to retryable offline problems`() = runTest {
        val error = IOException("offline").toNetworkFailure()

        assertProblem(ResourceProblemCategory.OFFLINE, retryable = true, error)
    }

    @Test
    fun `timeout transport failures map to retryable temporary problems`() = runTest {
        val error = HttpRequestTimeoutException(HttpRequestBuilder()).toNetworkFailure()

        assertProblem(ResourceProblemCategory.TEMPORARY, retryable = true, error)
    }

    @Test
    fun `decoding failures map to non-retryable permanent problems`() {
        val error = NetworkFailure.Decoding(IllegalArgumentException("invalid body"))

        assertProblem(ResourceProblemCategory.PERMANENT, retryable = false, error)
    }

    @Test
    fun `503 html responses map to retryable temporary problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.TEMPORARY,
            retryable = true,
            networkFailure(HttpStatusCode.ServiceUnavailable, "<html>down</html>"),
        )
    }

    @Test
    fun `429 responses map to retryable temporary problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.TEMPORARY,
            retryable = true,
            networkFailure(HttpStatusCode.TooManyRequests, "slow down"),
        )
    }

    @Test
    fun `request timeout responses map to retryable temporary problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.TEMPORARY,
            retryable = true,
            networkFailure(HttpStatusCode.RequestTimeout, "timed out"),
        )
    }

    @Test
    fun `unauthorized responses map to non-retryable access problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.ACCESS,
            retryable = false,
            networkFailure(HttpStatusCode.Unauthorized, "sign in"),
        )
    }

    @Test
    fun `forbidden responses map to non-retryable access problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.ACCESS,
            retryable = false,
            networkFailure(HttpStatusCode.Forbidden, "nope"),
        )
    }

    @Test
    fun `other client responses map to permanent problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.PERMANENT,
            retryable = false,
            networkFailure(HttpStatusCode.NotFound, "gone"),
        )
    }

    @Test
    fun `redirect responses map to unknown problems`() = runTest {
        assertProblem(
            ResourceProblemCategory.UNKNOWN,
            retryable = false,
            networkFailure(HttpStatusCode.NotModified, "not modified"),
        )
    }

    @Test
    fun `unexpected failures map to non-retryable unknown problems`() {
        val error = NetworkFailure.Unexpected(IllegalStateException("unexpected"))

        assertProblem(ResourceProblemCategory.UNKNOWN, retryable = false, error)
    }

    private fun assertProblem(
        category: ResourceProblemCategory,
        retryable: Boolean,
        error: NetworkFailure,
    ) {
        assertEquals(ResourceProblem(category, retryable), error.toResourceProblem())
    }

    private suspend fun networkFailure(status: HttpStatusCode, body: String): NetworkFailure {
        val client = HttpClient(MockEngine { respond(body, status) }) { expectSuccess = true }
        return requireNotNull(
            client.tryCatching { get("https://example.com/thing").bodyAsText() }.getError()
        )
    }
}
