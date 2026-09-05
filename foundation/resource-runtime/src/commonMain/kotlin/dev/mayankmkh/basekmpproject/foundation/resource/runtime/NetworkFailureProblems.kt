package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.TransportFailureKind
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory

/**
 * Turns a network result into the outcome of a sync attempt. A success is committed to the durable
 * store by [persist] and reported as [RefreshOutcome.Succeeded]; a failure is mapped with
 * [toResourceProblem] and reported as [RefreshOutcome.Failed]. A throw from [persist] propagates,
 * because a value that failed to land is a bug rather than a refresh problem.
 */
public suspend fun <T> Result<T, NetworkFailure>.commit(
    persist: suspend (T) -> Unit
): RefreshOutcome =
    fold(
        success = {
            persist(it)
            RefreshOutcome.Succeeded
        },
        failure = { RefreshOutcome.Failed(it.toResourceProblem()) },
    )

public fun NetworkFailure.toResourceProblem(): ResourceProblem =
    when (this) {
        is NetworkFailure.Transport ->
            when (kind) {
                TransportFailureKind.OFFLINE ->
                    ResourceProblem(category = ResourceProblemCategory.OFFLINE, retryable = true)
                TransportFailureKind.TIMEOUT ->
                    ResourceProblem(category = ResourceProblemCategory.TEMPORARY, retryable = true)
            }
        is NetworkFailure.Decoding ->
            ResourceProblem(category = ResourceProblemCategory.PERMANENT, retryable = false)
        is NetworkFailure.Http ->
            when {
                status == io.ktor.http.HttpStatusCode.Unauthorized ||
                    status == io.ktor.http.HttpStatusCode.Forbidden ->
                    ResourceProblem(ResourceProblemCategory.ACCESS, retryable = false)
                status == io.ktor.http.HttpStatusCode.RequestTimeout ||
                    status == io.ktor.http.HttpStatusCode.TooManyRequests ->
                    ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)
                status.value in HttpServerErrorLowerBound..HttpServerErrorUpperBound ->
                    ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)
                status.value in HttpClientErrorLowerBound..HttpClientErrorUpperBound ->
                    ResourceProblem(ResourceProblemCategory.PERMANENT, retryable = false)
                else -> ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = false)
            }
        is NetworkFailure.Unexpected ->
            ResourceProblem(category = ResourceProblemCategory.UNKNOWN, retryable = false)
    }

private const val HttpClientErrorLowerBound = 400
private const val HttpClientErrorUpperBound = 499
private const val HttpServerErrorLowerBound = 500
private const val HttpServerErrorUpperBound = 599
