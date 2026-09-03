package dev.mayankmkh.basekmpproject.foundation.resource.store5

import dev.mayankmkh.basekmpproject.foundation.network.ApiError
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailureKind
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory

public fun ApiError.toResourceProblem(): ResourceProblem =
    when (this) {
        is ApiError.Network ->
            when (kind) {
                NetworkFailureKind.OFFLINE ->
                    ResourceProblem(category = ResourceProblemCategory.OFFLINE, retryable = true)
                NetworkFailureKind.TIMEOUT ->
                    ResourceProblem(category = ResourceProblemCategory.TEMPORARY, retryable = true)
            }
        is ApiError.Http ->
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
        is ApiError.Unknown ->
            ResourceProblem(category = ResourceProblemCategory.UNKNOWN, retryable = false)
    }

private const val HttpClientErrorLowerBound = 400
private const val HttpClientErrorUpperBound = 499
private const val HttpServerErrorLowerBound = 500
private const val HttpServerErrorUpperBound = 599
