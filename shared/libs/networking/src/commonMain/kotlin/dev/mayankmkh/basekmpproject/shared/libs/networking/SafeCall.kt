package dev.mayankmkh.basekmpproject.shared.libs.networking

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.native.concurrent.ThreadLocal
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
                    else -> ApiError.Unknown(it)
                }
            } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
                ApiError.Unknown(throwable)
            }
        }
}

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
