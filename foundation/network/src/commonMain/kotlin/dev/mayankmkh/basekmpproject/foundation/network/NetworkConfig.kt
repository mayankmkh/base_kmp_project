package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.http.Url
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class NetworkConfig(
    public val baseUrl: Url,
    public val defaultHeaders: Map<String, String> = emptyMap(),
    public val requestTimeout: Duration = 30.seconds,
    public val connectTimeout: Duration = 10.seconds,
    public val socketTimeout: Duration = 30.seconds,
)
