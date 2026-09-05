package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.plugins.ResponseException
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode

public sealed interface NetworkFailure {
    public val cause: Throwable

    /** The `X-Request-Id` of the attempt that failed; support and logs correlate on it. */
    public val requestId: String?

    /** Non-success status. Body bytes are copied out; decode with [bodyOrNull]. */
    public data class Http(
        public val status: HttpStatusCode,
        public val headers: Headers,
        public val body: ByteArray,
        public override val requestId: String?,
        public override val cause: ResponseException,
    ) : NetworkFailure

    /** Nothing usable came back. */
    public data class Transport(
        public val kind: TransportFailureKind,
        public override val requestId: String?,
        public override val cause: Throwable,
    ) : NetworkFailure

    /** A success status whose body did not match the requested type. */
    public data class Decoding(
        public override val requestId: String?,
        public override val cause: Throwable,
    ) : NetworkFailure

    public data class Unexpected(
        public override val requestId: String?,
        public override val cause: Throwable,
    ) : NetworkFailure
}

/** The response status of an HTTP failure, null when nothing came back. */
public val NetworkFailure.status: HttpStatusCode?
    get() = (this as? NetworkFailure.Http)?.status

public enum class TransportFailureKind {
    OFFLINE,
    TIMEOUT,
}
