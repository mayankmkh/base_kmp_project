package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.js.JsError

// Browser fetch rejection is wrapped in Error("Fail to fetch", JsError(...)); body stream
// rejection can surface JsError directly.
internal actual fun Throwable.platformTransportFailureKind(): TransportFailureKind? =
    if (this is JsError || cause is JsError) TransportFailureKind.OFFLINE else null
