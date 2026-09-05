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
import io.ktor.http.HttpStatusCode

/**
 * Turns an implementation-only network result into a command outcome.
 *
 * Endpoint answer statuses must already be values in this result. [onFailure] may roll back an
 * optimistic write before the failure is classified. This is the only bridge that classifies and
 * logs [NetworkFailure], so every [Outcome.Failed] it creates has exactly one structured log entry.
 */
public suspend fun <T, R> Result<T, NetworkFailure>.toOutcome(
    logger: Logger,
    operation: String,
    onFailure: suspend (NetworkFailure) -> Unit = {},
    onCompleted: suspend (T) -> R,
): Outcome<R> {
    return fold(
        success = { Outcome.Completed(onCompleted(it)) },
        failure = { failure ->
            onFailure(failure)
            val problem = failure.toProblem()
            failure.logClassification(logger, operation, problem)
            Outcome.Failed(problem)
        },
    )
}

/** Commits a successful network value and reports a library-free command outcome. */
public suspend fun <T> Result<T, NetworkFailure>.commit(
    logger: Logger,
    operation: String,
    persist: suspend (T) -> Unit,
): Outcome<Unit> = toOutcome(logger, operation) { value -> persist(value) }

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

private fun NetworkFailure.logClassification(logger: Logger, operation: String, problem: Problem) {
    // Built inside the lambda so a filtered severity costs nothing.
    val message = {
        "network_failure" +
            " operation=$operation" +
            " kind=${problem.kind}" +
            " httpStatus=${status?.value}" +
            " transportKind=${(this as? NetworkFailure.Transport)?.kind}" +
            " requestId=${problem.reference}" +
            " exceptionClass=${cause::class.simpleName}" +
            " exceptionMessage=${cause.message}"
    }
    if (problem.kind == ProblemKind.UNEXPECTED) logger.e(message = message)
    else logger.w(message = message)
}
