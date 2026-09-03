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
        is ApiError.ServerResponse ->
            ResourceProblem(category = ResourceProblemCategory.TEMPORARY, retryable = true)
        is ApiError.ClientRequest.Forbidden,
        is ApiError.ClientRequest.Unauthorized ->
            ResourceProblem(category = ResourceProblemCategory.ACCESS, retryable = false)
        is ApiError.ClientRequest.BadRequest,
        is ApiError.ClientRequest.Other ->
            ResourceProblem(category = ResourceProblemCategory.PERMANENT, retryable = false)
        is ApiError.Redirect,
        is ApiError.OtherResponse,
        is ApiError.Unknown ->
            ResourceProblem(category = ResourceProblemCategory.UNKNOWN, retryable = false)
    }
