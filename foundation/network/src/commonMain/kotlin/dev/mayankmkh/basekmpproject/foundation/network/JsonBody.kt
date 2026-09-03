package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

public inline fun <reified T> HttpRequestBuilder.jsonBody(value: T) {
    contentType(ContentType.Application.Json)
    setBody(value)
}
