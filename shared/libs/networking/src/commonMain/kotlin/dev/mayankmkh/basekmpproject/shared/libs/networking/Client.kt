package dev.mayankmkh.basekmpproject.shared.libs.networking

import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.throwIf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

fun createJson() = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

private const val RequestTimeoutMillis = 30_000L
private const val SocketTimeoutMillis = 30_000L

@Suppress("LongMethod", "LongParameterList")
fun createHttpClient(
    config: NetworkConfig,
    bearerTokenSource: BearerTokenSource,
    clientLogger: Logger,
    json: Json = createJson(),
    logLevel: LogLevel = LogLevel.HEADERS,
): HttpClient {
    return HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = RequestTimeoutMillis
            socketTimeoutMillis = SocketTimeoutMillis
        }
        install(Logging) {
            logger = ktorPlatformLogger(clientLogger)
            level = logLevel
        }
        expectSuccess = true
        defaultRequest {
            // https://github.com/ktorio/ktor/issues/537
            url {
                if (host == "localhost") {
                    protocol = config.baseUrl.protocol
                    host = config.baseUrl.host
                }
                if (contentType() == null) {
                    contentType(config.contentType)
                }
            }
            config.defaultHeaders.forEach { (key, value) -> header(key, value) }
        }

        val tokenClient = tokenClient(json, logLevel, clientLogger)
        install(Auth) {
            bearer {
                sendWithoutRequest { it.isAuthenticationEnabled() }
                loadTokens { getBearerTokensFromSource(bearerTokenSource) }
                refreshTokens {
                    runCatching {
                            val bearerTokens = getBearerTokensFromSource(bearerTokenSource)
                            if (oldTokens?.accessToken != bearerTokens?.accessToken) {
                                bearerTokens
                            } else {
                                refreshTokenFromClient(bearerTokenSource, tokenClient)
                                getBearerTokensFromSource(bearerTokenSource)
                            }
                        }
                        .throwIf { it is CancellationException }
                        .getOrElse { getBearerTokensFromSource(bearerTokenSource) }
                }
            }
        }
    }
}

fun tokenClient(json: Json, logLevel: LogLevel, clientLogger: Logger) = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
    install(Logging) {
        logger = clientLogger
        level = logLevel
    }
}

private suspend fun refreshTokenFromClient(
    bearerTokenSource: BearerTokenSource,
    clientWithAuth: HttpClient,
) {
    with(bearerTokenSource) {
        try {
            clientWithAuth.refreshToken()
        } catch (exception: ClientRequestException) {
            if (exception.response.status == HttpStatusCode.Unauthorized) {
                bearerTokenSource.refreshUnauthorized()
            }
            throw exception
        }
    }
}

private suspend fun getBearerTokensFromSource(bearerTokenSource: BearerTokenSource): BearerTokens? {
    val authToken: String = bearerTokenSource.getAuthToken() ?: return null
    return BearerTokens(accessToken = authToken, refreshToken = bearerTokenSource.getRefreshToken())
}

interface BearerTokenSource {
    suspend fun getAuthToken(): String?

    suspend fun getRefreshToken(): String?

    suspend fun HttpClient.refreshToken()

    suspend fun refreshUnauthorized()
}

private val noAuthAttribute = AttributeKey<Unit>("NoAuthAttributeKey")

fun HttpRequestBuilder.disableAuthentication() {
    attributes.put(noAuthAttribute, Unit)
}

private fun HttpRequestBuilder.isAuthenticationEnabled(): Boolean {
    return attributes.contains(noAuthAttribute).not()
}
