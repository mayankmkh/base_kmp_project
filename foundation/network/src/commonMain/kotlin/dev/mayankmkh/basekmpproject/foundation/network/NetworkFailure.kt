package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.plugins.ResponseException
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode

public sealed interface NetworkFailure {
    public val cause: Throwable

    /** Non-success status. Body bytes are copied out; decode with [bodyOrNull]. */
    public data class Http(
        public val status: HttpStatusCode,
        public val headers: Headers,
        public val body: ByteArray,
        public val requestId: String?,
        public override val cause: ResponseException,
    ) : NetworkFailure

    /** Nothing usable came back. */
    public data class Transport(
        public val kind: TransportFailureKind,
        public override val cause: Throwable,
    ) : NetworkFailure

    /** A success status whose body did not match the requested type. */
    public data class Decoding(public override val cause: Throwable) : NetworkFailure

    public data class Unexpected(public override val cause: Throwable) : NetworkFailure
}

public enum class TransportFailureKind {
    OFFLINE,
    TIMEOUT,
}
