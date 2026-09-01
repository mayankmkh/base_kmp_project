package dev.mayankmkh.basekmpproject.shared.libs.networking

import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.throwIf
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.takeFrom
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

@Suppress("LongParameterList")
fun createHttpClient(
    config: NetworkConfig,
    bearerTokenSource: BearerTokenSource,
    clientLogger: Logger,
    json: Json = createJson(),
    logLevel: LogLevel = LogLevel.HEADERS,
): HttpClient = HttpClient {
    installNetworking(
        config,
        bearerTokenSource,
        json,
        tokenClient(json, logLevel, clientLogger),
        ktorPlatformLogger(clientLogger),
        logLevel,
    )
}

/**
 * The same client, on an engine the caller names.
 *
 * The no-engine overload above resolves one off the classpath, which is right for the app and
 * useless for a test: there is no seam to answer a request without a network. Handing in a
 * `MockEngine` here gets the real plugin stack -- content negotiation, auth, `expectSuccess` --
 * over canned responses. Kept in `main` for the same reason `createInMemoryDriver` is: a KMP test
 * source set is not visible outside its own module.
 */
@Suppress("LongParameterList")
fun createHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    bearerTokenSource: BearerTokenSource = AnonymousBearerTokenSource,
    clientLogger: Logger = Logger.EMPTY,
    json: Json = createJson(),
    logLevel: LogLevel = LogLevel.NONE,
): HttpClient =
    HttpClient(engine) {
        installNetworking(
            config,
            bearerTokenSource,
            json,
            tokenClient(json, logLevel, clientLogger),
            clientLogger,
            logLevel,
        )
    }

/**
 * Split out of [createHttpClient] so the engine stays the caller's choice: `HttpClient { }`
 * resolves one off the classpath, which leaves no seam for a test to answer requests without a
 * network.
 */
@Suppress("LongMethod", "LongParameterList")
internal fun HttpClientConfig<*>.installNetworking(
    config: NetworkConfig,
    bearerTokenSource: BearerTokenSource,
    json: Json,
    tokenClient: HttpClient,
    clientLogger: Logger,
    logLevel: LogLevel,
) {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        requestTimeoutMillis = RequestTimeoutMillis
        socketTimeoutMillis = SocketTimeoutMillis
    }
    install(Logging) {
        logger = clientLogger
        level = logLevel
    }
    expectSuccess = true
    defaultRequest {
        // Only fills in what the call left out: `DefaultRequest` keeps a request's own host when it
        // has one, so an absolute url still goes where it says.
        url.takeFrom(config.baseUrl)
        if (contentType() == null) {
            contentType(config.contentType)
        }
        config.defaultHeaders.forEach { (key, value) -> header(key, value) }
    }

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

/**
 * The token source for an app that has no sign-in yet.
 *
 * Returning `null` is what keeps the `Auth` plugin quiet: with no access token it adds no header
 * and never reaches the refresh path. A real app replaces this binding with one reading from
 * storage -- the rest of the networking stack does not change.
 */
object AnonymousBearerTokenSource : BearerTokenSource {
    override suspend fun getAuthToken(): String? = null

    override suspend fun getRefreshToken(): String? = null

    override suspend fun HttpClient.refreshToken() = Unit

    override suspend fun refreshUnauthorized() = Unit
}

private val noAuthAttribute = AttributeKey<Unit>("NoAuthAttributeKey")

fun HttpRequestBuilder.disableAuthentication() {
    attributes.put(noAuthAttribute, Unit)
}

private fun HttpRequestBuilder.isAuthenticationEnabled(): Boolean {
    return attributes.contains(noAuthAttribute).not()
}
