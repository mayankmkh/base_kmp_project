package dev.mayankmkh.basekmpproject.foundation.network

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
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
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.util.network.UnresolvedAddressException
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

public suspend fun <T> HttpClient.tryCatching(
    block: suspend HttpClient.() -> T
): Result<T, NetworkFailure> {
    // Seeded up front so a failure raised before any attempt is sent still carries a reference;
    // each attempt the client sends replaces it with that attempt's own id.
    val requestIds = RequestIdContext(latest = Uuid.random().toString())
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

/**
 * Treats a response with [status] as an answer of this endpoint: that failure becomes `Ok` of
 * [answer] and every other failure stays `Err`. Chain one call per status the endpoint answers
 * with, so the implementation states only the mapping and the generic bridge sees the rest.
 */
public inline fun <T> Result<T, NetworkFailure>.answerOn(
    status: HttpStatusCode,
    answer: (NetworkFailure.Http) -> T,
): Result<T, NetworkFailure> =
    recoverIf({ it.status == status }) { answer(it as NetworkFailure.Http) }

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
