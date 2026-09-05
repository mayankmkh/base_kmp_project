package dev.mayankmkh.basekmpproject.foundation.network

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.request
import io.ktor.client.utils.unwrapCancellationException
import io.ktor.serialization.JsonConvertException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

public suspend fun <T> HttpClient.tryCatching(
    block: suspend HttpClient.() -> T
): Result<T, NetworkFailure> {
    val requestIds = RequestIdContext()
    return runCatching { withContext(requestIds) { block() } }
        .mapError { it.toNetworkFailure(requestIds.latest) }
}

public inline fun <reified T> NetworkFailure.Http.bodyOrNull(json: Json): T? =
    try {
        json.decodeFromString<T>(body.decodeToString())
    } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") exception: Throwable) {
        if (exception.unwrapCancellationException() is CancellationException) throw exception
        else null
    }

public suspend fun Throwable.toNetworkFailure(): NetworkFailure = toNetworkFailure(requestId = null)

private suspend fun Throwable.toNetworkFailure(requestId: String?): NetworkFailure {
    val unwrapped = unwrapCancellationException()
    if (unwrapped is CancellationException) throw unwrapped

    return when (unwrapped) {
        is ResponseException ->
            NetworkFailure.Http(
                status = unwrapped.response.status,
                headers = unwrapped.response.headers,
                body = unwrapped.response.bodyAsBytes(),
                requestId = unwrapped.response.request.headers[RequestIdHeader] ?: requestId,
                cause = unwrapped,
            )
        is JsonConvertException,
        is NoTransformationFoundException -> NetworkFailure.Decoding(requestId, unwrapped)
        is HttpRequestTimeoutException,
        is SocketTimeoutException,
        is ConnectTimeoutException ->
            NetworkFailure.Transport(TransportFailureKind.TIMEOUT, requestId, unwrapped)
        is UnresolvedAddressException,
        is IOException ->
            NetworkFailure.Transport(TransportFailureKind.OFFLINE, requestId, unwrapped)
        else -> {
            val kind = unwrapped.platformTransportFailureKind()
            if (kind == null) NetworkFailure.Unexpected(requestId, unwrapped)
            else NetworkFailure.Transport(kind, requestId, unwrapped)
        }
    }
}

internal expect fun Throwable.platformTransportFailureKind(): TransportFailureKind?
