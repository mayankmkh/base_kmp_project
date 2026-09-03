package dev.mayankmkh.basekmpproject.foundation.network

// OkHttp transport failures already surface as kotlinx.io.IOException through its JVM typealias.
internal actual fun Throwable.platformNetworkFailureKind(): NetworkFailureKind? = null
