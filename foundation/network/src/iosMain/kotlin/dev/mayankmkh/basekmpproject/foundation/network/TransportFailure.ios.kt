package dev.mayankmkh.basekmpproject.foundation.network

// DarwinHttpRequestException extends kotlinx.io.IOException; Darwin timeouts become the typed
// SocketTimeoutException handled in common code.
internal actual fun Throwable.platformNetworkFailureKind(): NetworkFailureKind? = null
