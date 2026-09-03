package dev.mayankmkh.basekmpproject.foundation.network

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/** Refer [io.ktor.client.plugins.addDefaultResponseValidation] */
public suspend inline fun <reified T> HttpClient.tryCatching(
    block: suspend HttpClient.() -> T
): Result<T, ApiError> {
    return runCatching { block() }
        .mapError {
            when (it) {
                is ResponseException -> ApiError.Http(it.response.status, it.response, it)
                else -> it.toApiError()
            }
        }
}

/**
 * Decodes the error body as [T] with the client's serializer, or null when it is not that shape.
 */
public suspend inline fun <reified T> ApiError.Http.bodyOrNull(): T? =
    try {
        response.body<T>()
    } catch (exception: CancellationException) {
        throw exception
    } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") exception: Exception) {
        // Error payloads are optional and may not match the Capability-owned DTO.
        null
    }

public fun Throwable.toApiError(): ApiError {
    if (this is CancellationException) throw this

    val kind =
        when (this) {
            is HttpRequestTimeoutException,
            is SocketTimeoutException,
            is ConnectTimeoutException -> NetworkFailureKind.TIMEOUT
            is UnresolvedAddressException,
            is IOException -> NetworkFailureKind.OFFLINE
            else -> platformNetworkFailureKind()
        }
    return if (kind == null) ApiError.Unknown(this) else ApiError.Network(kind, this)
}

internal expect fun Throwable.platformNetworkFailureKind(): NetworkFailureKind?
