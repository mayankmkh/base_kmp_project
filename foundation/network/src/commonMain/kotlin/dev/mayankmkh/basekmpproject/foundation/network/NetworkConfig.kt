package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.http.ContentType
import io.ktor.http.Url

data class NetworkConfig(
    val baseUrl: Url,
    val contentType: ContentType = ContentType.Application.Json,
    val defaultHeaders: Map<String, Any>,
)
