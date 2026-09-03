package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
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
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal fun createJson(): Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

@Suppress("LongParameterList")
public fun createHttpClient(
    config: NetworkConfig,
    credentialProvider: CredentialProvider,
    clientLogger: Logger,
    json: Json = createJson(),
    logLevel: LogLevel = LogLevel.HEADERS,
): HttpClient = HttpClient {
    installNetworking(
        config,
        credentialProvider,
        json,
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
public fun createHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    credentialProvider: CredentialProvider = AnonymousCredentialProvider,
    clientLogger: Logger = Logger.EMPTY,
    json: Json = createJson(),
    logLevel: LogLevel = LogLevel.NONE,
): HttpClient =
    HttpClient(engine) {
        installNetworking(
            config,
            credentialProvider,
            json,
            ktorPlatformLogger(clientLogger),
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
    credentialProvider: CredentialProvider,
    json: Json,
    clientLogger: Logger,
    logLevel: LogLevel,
) {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeout.inWholeMilliseconds
        connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
        socketTimeoutMillis = config.socketTimeout.inWholeMilliseconds
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
        config.defaultHeaders.forEach { (key, value) -> header(key, value) }
        headers[RequestIdHeader] = Uuid.random().toString()
    }

    install(Auth) {
        // One predicate feeds both gates, so a request either carries the base host's credentials
        // and may be refreshed on 401, or is left alone entirely. Without the response gate Ktor
        // answers any 401 by retrying with the cached token first.
        reAuthorizeOnResponse { response ->
            response.status == HttpStatusCode.Unauthorized &&
                wantsCredentials(response.request.attributes, response.request.url.host, config)
        }
        bearer {
            sendWithoutRequest { wantsCredentials(it.attributes, it.url.host, config) }
            loadTokens {
                credentialProvider.currentBearerToken()?.let { BearerTokens(it, null) }
            }
            refreshTokens {
                // Another caller may already have refreshed while this request was in flight.
                val current = credentialProvider.currentBearerToken()
                if (current != null && current != oldTokens?.accessToken) {
                    return@refreshTokens BearerTokens(current, null)
                }

                try {
                    when (val result = credentialProvider.refreshBearerToken()) {
                        is CredentialRefreshResult.Refreshed ->
                            BearerTokens(result.bearerToken, null)
                        CredentialRefreshResult.Rejected,
                        CredentialRefreshResult.Unavailable -> null
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (
                    @Suppress("SwallowedException", "TooGenericExceptionCaught")
                    exception: Exception) {
                    // A throwing provider counts as `Unavailable`: the caller gets the original
                    // 401 and the session stays as the provider left it.
                    null
                }
            }
        }
    }
}

private fun wantsCredentials(attributes: Attributes, host: String, config: NetworkConfig): Boolean =
    !attributes.contains(noAuthAttribute) && host.equals(config.baseUrl.host, ignoreCase = true)

/**
 * The Identity Capability's side of the neutral network inversion (§18.6.1). Foundation network
 * asks for the current bearer token before an authenticated request and for a refresh after a 401;
 * what a refresh means for the session -- sign-out, re-login, nothing -- is the provider's call.
 */
public interface CredentialProvider {
    public suspend fun currentBearerToken(): String?

    public suspend fun refreshBearerToken(): CredentialRefreshResult
}

public sealed interface CredentialRefreshResult {
    public data class Refreshed(public val bearerToken: String) : CredentialRefreshResult

    /** The credentials are invalid; the provider has already applied its session semantics. */
    public data object Rejected : CredentialRefreshResult

    /** Refresh could not run right now (offline, server down); the session is unchanged. */
    public data object Unavailable : CredentialRefreshResult
}

public object AnonymousCredentialProvider : CredentialProvider {
    public override suspend fun currentBearerToken(): String? = null

    public override suspend fun refreshBearerToken(): CredentialRefreshResult =
        CredentialRefreshResult.Rejected
}

private const val RequestIdHeader = "X-Request-Id"

private val noAuthAttribute = AttributeKey<Unit>("NoAuthAttributeKey")

/** Marks one request as anonymous: no bearer token is attached and a 401 is never retried. */
public fun HttpRequestBuilder.disableAuthentication() {
    attributes.put(noAuthAttribute, Unit)
}
