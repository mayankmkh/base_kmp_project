package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/**
 * What `tryCatching` hands back when a call fails. Status comes first; an error body is decoded
 * only when the Capability asks for it through [ApiError.Http.bodyOrNull].
 */
public sealed interface ApiError {
    public val cause: Throwable

    /** Transport failure before any response arrived. */
    public data class Network(
        public val kind: NetworkFailureKind,
        public override val cause: Throwable,
    ) : ApiError

    /** The server answered with a non-success status. The body is still readable via [response]. */
    public data class Http(
        public val status: HttpStatusCode,
        public val response: HttpResponse,
        public override val cause: ResponseException,
    ) : ApiError

    public data class Unknown(public override val cause: Throwable) : ApiError
}

/** Transport failures the client can tell apart by type. Anything else stays [ApiError.Unknown]. */
public enum class NetworkFailureKind {
    OFFLINE,
    TIMEOUT,
}
