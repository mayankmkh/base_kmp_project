package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.http.Url
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class NetworkConfig(
    public val baseUrl: Url,
    public val requestTimeout: Duration = 30.seconds,
    public val connectTimeout: Duration = 10.seconds,
    public val socketTimeout: Duration = 30.seconds,
    /**
     * Ktor's own default is `HEADERS`; the app raises this for debug builds only (network.md §10).
     */
    public val logLevel: LogLevel = LogLevel.NONE,
) {
    init {
        require(baseUrl.encodedPath.isEmpty() || baseUrl.encodedPath.endsWith("/")) {
            "NetworkConfig.baseUrl path must be empty or end with '/'."
        }
    }
}

/** Header values that may change between requests. Read once per request and must not suspend. */
public fun interface DynamicHeaders {
    public fun current(): Map<String, String>

    public companion object {
        public val None: DynamicHeaders = DynamicHeaders { emptyMap() }
    }
}
