package dev.mayankmkh.basekmpproject.shared.libs.networking

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ApiError(val throwable: Throwable) {
    data class Redirect(val exception: RedirectResponseException) : ApiError(exception)

    sealed class ClientRequest(exception: ClientRequestException) : ApiError(exception) {
        data class BadRequest(private val e: ClientRequestException, val errors: BadRequestErrors) :
            ClientRequest(e)

        data class Forbidden(private val e: ClientRequestException, val clientError: ClientError) :
            ClientRequest(e)

        data class Other(private val e: ClientRequestException, val clientError: ClientError) :
            ClientRequest(e)
    }

    data class ServerResponse(
        val exception: ServerResponseException,
        val serverError: ServerError,
    ) : ApiError(exception)

    data class OtherResponse(val exception: ResponseException) : ApiError(exception)

    data class Unknown(private val t: Throwable) : ApiError(t)
}

@Serializable
data class BadRequestErrors(@SerialName("errors") val errors: Map<Field, List<String>>)

@Serializable data class ClientError(@SerialName("message") val message: String)

@Serializable data class ServerError(@SerialName("errors") val errors: String)

typealias Field = String
