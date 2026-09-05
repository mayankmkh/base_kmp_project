package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.TransportFailureKind
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

@OptIn(ExperimentalKermitApi::class)
class CommandBridgeTest {
    @Test
    fun `commit persists a success without logging`() = runTest {
        val result: Result<String, NetworkFailure> = Ok("value")
        val persisted = mutableListOf<String>()
        val logs = TestLogWriter(Severity.Verbose)

        val outcome = logs.bridge("posts").commit(result, "refresh", persisted::add)

        assertEquals(Outcome.Completed(Unit), outcome)
        assertEquals(listOf("value"), persisted)
        assertTrue(logs.logs.isEmpty())
    }

    @Test
    fun `bridge maps and logs an offline failure once without completing`() = runTest {
        val failure =
            NetworkFailure.Transport(
                kind = TransportFailureKind.OFFLINE,
                requestId = "request-1",
                cause = IOException("offline"),
            )
        val result: Result<String, NetworkFailure> = Err(failure)
        val logs = TestLogWriter(Severity.Verbose)
        var completionCalls = 0

        val outcome =
            logs.bridge("todos").toOutcome(result, "create") {
                completionCalls++
                it
            }

        assertEquals(0, completionCalls)
        assertEquals(
            Problem(ProblemKind.OFFLINE, "request-1"),
            assertIs<Outcome.Failed>(outcome).problem,
        )
        assertEquals(1, logs.logs.size)
        assertEquals(Severity.Warn, logs.logs.single().severity)
        assertEquals("todos", logs.logs.single().tag)
        assertTrue(logs.logs.single().message.contains("operation=todos.create"))
        assertTrue(logs.logs.single().message.contains("kind=OFFLINE"))
        assertTrue(logs.logs.single().message.contains("transportKind=OFFLINE"))
        assertTrue(logs.logs.single().message.contains("requestId=request-1"))
        assertTrue(logs.logs.single().message.contains("exceptionClass=IOException"))
        assertTrue(logs.logs.single().message.contains("exceptionMessage=offline"))
    }

    @Test
    fun `unexpected classifications log at error severity`() = runTest {
        val failure =
            NetworkFailure.Unexpected(
                requestId = "request-2",
                cause = IllegalStateException("unexpected"),
            )
        val logs = TestLogWriter(Severity.Verbose)
        val result: Result<String, NetworkFailure> = Err(failure)

        val outcome = logs.bridge("todos").toOutcome(result, "rename") { it }

        assertEquals(
            Problem(ProblemKind.UNEXPECTED, "request-2"),
            assertIs<Outcome.Failed>(outcome).problem,
        )
        assertEquals(Severity.Error, logs.logs.single().severity)
    }

    @Test
    fun `a runtime failure with a cause logs once at error severity`() {
        val logs = TestLogWriter(Severity.Verbose)

        val problem = logs.bridge("posts").unexpected("sync(Unit)", IllegalStateException("boom"))

        assertEquals(Problem(ProblemKind.UNEXPECTED), problem)
        val entry = logs.logs.single()
        assertEquals(Severity.Error, entry.severity)
        assertEquals("posts", entry.tag)
        assertTrue(entry.message.contains("operation=posts.sync(Unit)"))
        assertTrue(entry.message.contains("kind=UNEXPECTED"))
        assertTrue(entry.message.contains("exceptionClass=IllegalStateException"))
        assertTrue(entry.message.contains("exceptionMessage=boom"))
    }

    @Test
    fun `network failures map to the stable taxonomy and preserve request ids`() = runTest {
        assertProblem(
            ProblemKind.OFFLINE,
            NetworkFailure.Transport(
                TransportFailureKind.OFFLINE,
                "offline-id",
                IOException("offline"),
            ),
            "offline-id",
        )
        assertProblem(
            ProblemKind.TIMEOUT,
            NetworkFailure.Transport(
                TransportFailureKind.TIMEOUT,
                "timeout-id",
                IOException("timeout"),
            ),
            "timeout-id",
        )
        assertProblem(ProblemKind.FORBIDDEN, networkFailure(HttpStatusCode.Unauthorized))
        assertProblem(ProblemKind.FORBIDDEN, networkFailure(HttpStatusCode.Forbidden))
        assertProblem(ProblemKind.SERVER, networkFailure(HttpStatusCode.RequestTimeout))
        assertProblem(ProblemKind.SERVER, networkFailure(HttpStatusCode.TooManyRequests))
        assertProblem(ProblemKind.SERVER, networkFailure(HttpStatusCode.ServiceUnavailable))
        assertProblem(ProblemKind.UNEXPECTED, networkFailure(HttpStatusCode.NotFound))
        assertProblem(ProblemKind.UNEXPECTED, networkFailure(HttpStatusCode.NotModified))
        assertProblem(
            ProblemKind.UNEXPECTED,
            NetworkFailure.Decoding("decoding-id", IllegalArgumentException("invalid body")),
            "decoding-id",
        )
        assertProblem(
            ProblemKind.UNEXPECTED,
            NetworkFailure.Unexpected("unexpected-id", IllegalStateException("unexpected")),
            "unexpected-id",
        )
    }

    private fun assertProblem(kind: ProblemKind, failure: NetworkFailure, reference: String) {
        assertEquals(Problem(kind, reference), failure.toProblem())
    }

    private fun assertProblem(kind: ProblemKind, failure: NetworkFailure) {
        assertEquals(kind, failure.toProblem().kind)
    }

    private suspend fun networkFailure(status: HttpStatusCode): NetworkFailure {
        val client =
            createHttpClient(
                MockEngine { respond("failure", status) },
                NetworkConfig(Url("https://example.com")),
            )
        return requireNotNull(client.tryCatching { get("thing").bodyAsText() }.getError())
    }
}

/** A bridge whose only writer is [this], so a test asserts on records instead of output. */
private fun TestLogWriter.bridge(tag: String): CommandBridge =
    CommandBridge(Logger(TestConfig(Severity.Verbose, listOf(this))), tag)
