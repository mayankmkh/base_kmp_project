package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import co.touchlab.kermit.Logger
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.ServerErrorStatuses
import dev.mayankmkh.basekmpproject.foundation.network.TransportFailureKind
import dev.mayankmkh.basekmpproject.foundation.network.status
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent
import io.ktor.http.HttpStatusCode

/**
 * One per capability: classifies and logs network failures under that capability's tag.
 *
 * A capability builds one bridge from its injected [Logger] and routes every command through it, so
 * no call site repeats the logger or the tag. This is the only thing that classifies or logs a
 * [NetworkFailure], and every [Outcome.Failed] it creates has exactly one structured log entry
 * whose `operation` field reads `"<tag>.<operation>"`.
 */
public class CommandBridge(logger: Logger, private val tag: String) {
    private val logger = logger.withTag(tag)

    /**
     * Turns an implementation-only network result into a command outcome.
     *
     * Endpoint answer statuses must already be values in [result]. [onFailure] may roll back an
     * optimistic write before the failure is classified.
     */
    public suspend fun <T, R> toOutcome(
        result: Result<T, NetworkFailure>,
        operation: String,
        onFailure: suspend (NetworkFailure) -> Unit = {},
        onCompleted: suspend (T) -> R,
    ): Outcome<R> =
        result.fold(
            success = { Outcome.Completed(onCompleted(it)) },
            failure = { failure ->
                onFailure(failure)
                val problem = failure.toProblem()
                logNetworkFailure(operation, problem, failure)
                Outcome.Failed(problem)
            },
        )

    /** Persists a successful value and reports a library-free outcome. */
    public suspend fun <T> commit(
        result: Result<T, NetworkFailure>,
        operation: String,
        persist: suspend (T) -> Unit,
    ): Outcome<Unit> = toOutcome(result, operation) { value -> persist(value) }

    /**
     * A defect the runtime caught itself rather than read off the wire, such as a sync worker that
     * threw: logs once at error severity and returns the matching [Problem].
     */
    public fun unexpected(operation: String, cause: Throwable): Problem {
        // Built inside the lambda so a filtered severity costs nothing.
        val message = {
            logEvent(
                "unexpected_failure",
                "operation" to "$tag.$operation",
                "kind" to ProblemKind.UNEXPECTED,
                "exceptionClass" to cause::class.simpleName,
                "exceptionMessage" to cause.message,
            )
        }
        logger.e(message = message)
        return Problem(ProblemKind.UNEXPECTED)
    }

    private fun logNetworkFailure(
        operation: String,
        problem: Problem,
        failure: NetworkFailure,
    ) {
        val message = {
            logEvent(
                "network_failure",
                "operation" to "$tag.$operation",
                "kind" to problem.kind,
                "httpStatus" to failure.status?.value,
                "transportKind" to (failure as? NetworkFailure.Transport)?.kind,
                "requestId" to problem.reference,
                "exceptionClass" to failure.cause::class.simpleName,
                "exceptionMessage" to failure.cause.message,
            )
        }
        if (problem.kind == ProblemKind.UNEXPECTED) logger.e(message = message)
        else logger.w(message = message)
    }
}

/** Maps implementation-only network diagnostics into the stable product-neutral taxonomy. */
internal fun NetworkFailure.toProblem(): Problem =
    Problem(
        kind =
            when (this) {
                is NetworkFailure.Transport ->
                    when (kind) {
                        TransportFailureKind.OFFLINE -> ProblemKind.OFFLINE
                        TransportFailureKind.TIMEOUT -> ProblemKind.TIMEOUT
                    }
                is NetworkFailure.Http ->
                    when (status) {
                        HttpStatusCode.Unauthorized,
                        HttpStatusCode.Forbidden -> ProblemKind.FORBIDDEN
                        HttpStatusCode.RequestTimeout,
                        HttpStatusCode.TooManyRequests -> ProblemKind.SERVER
                        else ->
                            if (status.value in ServerErrorStatuses) ProblemKind.SERVER
                            else ProblemKind.UNEXPECTED
                    }
                is NetworkFailure.Decoding,
                is NetworkFailure.Unexpected -> ProblemKind.UNEXPECTED
            },
        reference = requestId,
    )
