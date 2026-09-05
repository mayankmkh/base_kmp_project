package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
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

class NetworkFailureProblemsTest {
    @Test
    fun `commit persists a success without logging`() = runTest {
        val result: Result<String, NetworkFailure> = Ok("value")
        val persisted = mutableListOf<String>()
        val logs = RecordingLogWriter()

        val outcome = result.commit(logs.logger, "posts.refresh", persisted::add)

        assertEquals(Outcome.Completed(Unit), outcome)
        assertEquals(listOf("value"), persisted)
        assertTrue(logs.entries.isEmpty())
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
        val logs = RecordingLogWriter()
        var completionCalls = 0

        val outcome =
            result.toOutcome(logs.logger, "todos.create") {
                completionCalls++
                it
            }

        assertEquals(0, completionCalls)
        assertEquals(
            Problem(ProblemKind.OFFLINE, "request-1"),
            assertIs<Outcome.Failed>(outcome).problem,
        )
        assertEquals(1, logs.entries.size)
        assertEquals(Severity.Warn, logs.entries.single().severity)
        assertTrue(logs.entries.single().message.contains("operation=todos.create"))
        assertTrue(logs.entries.single().message.contains("kind=OFFLINE"))
        assertTrue(logs.entries.single().message.contains("transportKind=OFFLINE"))
        assertTrue(logs.entries.single().message.contains("requestId=request-1"))
        assertTrue(logs.entries.single().message.contains("exceptionClass=IOException"))
        assertTrue(logs.entries.single().message.contains("exceptionMessage=offline"))
    }

    @Test
    fun `unexpected classifications log at error severity`() = runTest {
        val failure =
            NetworkFailure.Unexpected(
                requestId = "request-2",
                cause = IllegalStateException("unexpected"),
            )
        val logs = RecordingLogWriter()
        val result: Result<String, NetworkFailure> = Err(failure)

        val outcome = result.toOutcome(logs.logger, "todos.rename") { it }

        assertEquals(
            Problem(ProblemKind.UNEXPECTED, "request-2"),
            assertIs<Outcome.Failed>(outcome).problem,
        )
        assertEquals(Severity.Error, logs.entries.single().severity)
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

    private fun assertProblem(
        kind: ProblemKind,
        failure: NetworkFailure,
        reference: String? = failure.toProblem().reference,
    ) {
        assertEquals(Problem(kind, reference), failure.toProblem())
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

private class RecordingLogWriter : LogWriter() {
    val entries = mutableListOf<LogEntry>()
    val logger = Logger(StaticConfig(logWriterList = listOf(this)))

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        entries += LogEntry(severity, message)
    }
}

private data class LogEntry(val severity: Severity, val message: String)
