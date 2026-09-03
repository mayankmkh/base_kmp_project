package dev.mayankmkh.basekmpproject.foundation.network

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import kotlin.native.concurrent.ThreadLocal
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

@ThreadLocal
private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

/** Refer [io.ktor.client.plugins.addDefaultResponseValidation] */
suspend inline fun <reified T> HttpClient.tryCatching(
    block: HttpClient.() -> T
): Result<T, ApiError> {
    return runCatching { block() }
        .mapError {
            try {
                when (it) {
                    is ResponseException -> it.toApiClientRequestError()
                    else -> it.toApiError()
                }
            } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
                throwable.toApiError()
            }
        }
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

suspend fun ResponseException.toApiClientRequestError(): ApiError {
    return when (this) {
        is RedirectResponseException -> toApiRedirectError()
        is ClientRequestException -> toApiClientRequestError()
        is ServerResponseException -> toApiServerResponseError()
        else -> ApiError.OtherResponse(this)
    }
}

private fun RedirectResponseException.toApiRedirectError() = ApiError.Redirect(this)

private suspend fun ClientRequestException.toApiClientRequestError(): ApiError.ClientRequest {
    return when (this.response.status.value) {
        HttpStatusCode.BadRequest.value -> {
            val errors = json.decodeFromString(BadRequestErrors.serializer(), response.bodyAsText())
            ApiError.ClientRequest.BadRequest(this, errors)
        }
        HttpStatusCode.Unauthorized.value -> {
            val clientError = json.decodeFromString(ClientError.serializer(), response.bodyAsText())
            ApiError.ClientRequest.Unauthorized(this, clientError)
        }
        HttpStatusCode.Forbidden.value -> {
            val clientError = json.decodeFromString(ClientError.serializer(), response.bodyAsText())
            ApiError.ClientRequest.Forbidden(this, clientError)
        }
        else -> {
            val clientError = json.decodeFromString(ClientError.serializer(), response.bodyAsText())
            ApiError.ClientRequest.Other(this, clientError)
        }
    }
}

private suspend fun ServerResponseException.toApiServerResponseError(): ApiError.ServerResponse {
    val serverError = json.decodeFromString(ServerError.serializer(), response.bodyAsText())
    return ApiError.ServerResponse(this, serverError)
}
