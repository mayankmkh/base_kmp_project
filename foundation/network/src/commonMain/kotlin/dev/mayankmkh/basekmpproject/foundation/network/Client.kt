package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.client.utils.unwrapCancellationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json

internal expect val platformEngineFactory: HttpClientEngineFactory<*>

internal fun createJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

@Suppress("LongParameterList")
public fun createHttpClient(
    config: NetworkConfig,
    credentialProvider: CredentialProvider = AnonymousCredentialProvider,
    headers: DynamicHeaders = DynamicHeaders.None,
    clientLogger: Logger = Logger.EMPTY,
    json: Json = createJson(),
): HttpClient =
    HttpClient(platformEngineFactory) {
        installNetworking(config, credentialProvider, headers, clientLogger, json)
    }

/**
 * The same client on an engine the caller names; tests pass `MockEngine` and get the real plugin
 * stack over canned responses. Kept in `main` because a KMP test source set is not visible outside
 * its own module.
 */
@Suppress("LongParameterList")
public fun createHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    credentialProvider: CredentialProvider = AnonymousCredentialProvider,
    headers: DynamicHeaders = DynamicHeaders.None,
    clientLogger: Logger = Logger.EMPTY,
    json: Json = createJson(),
): HttpClient =
    HttpClient(engine) {
        installNetworking(config, credentialProvider, headers, clientLogger, json)
    }

@Suppress("LongParameterList")
private fun HttpClientConfig<*>.installNetworking(
    config: NetworkConfig,
    credentialProvider: CredentialProvider,
    headers: DynamicHeaders,
    clientLogger: Logger,
    json: Json,
) {
    installRetry()
    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeout.inWholeMilliseconds
        connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
        socketTimeoutMillis = config.socketTimeout.inWholeMilliseconds
    }
    install(ContentNegotiation) { json(json) }
    install(Logging) {
        logger = ktorPlatformLogger(clientLogger)
        level = config.logLevel
        sanitizeHeader { header ->
            header == HttpHeaders.Authorization ||
                header == HttpHeaders.Cookie ||
                header == HttpHeaders.SetCookie
        }
    }
    installAuthentication(config, credentialProvider)
    install(RequestId)
    defaultRequest {
        // Fills in only what the call left out: an absolute url keeps its own host.
        url.takeFrom(config.baseUrl)
        headers.current().forEach { (name, value) -> header(name, value) }
    }
    expectSuccess = true
}

private fun HttpClientConfig<*>.installRetry() {
    install(HttpRequestRetry) {
        maxRetries = MaximumRetries
        retryIf { request, response ->
            request.attributes.contains(Retryable) &&
                request.method in IdempotentMethods &&
                (response.status.value in ServerErrorStatuses ||
                    response.status == HttpStatusCode.TooManyRequests)
        }
        retryOnExceptionIf { request, cause ->
            val unwrapped = cause.unwrapCancellationException()
            request.attributes.contains(Retryable) &&
                request.method in IdempotentMethods &&
                cause !is CancellationException &&
                unwrapped !is CancellationException &&
                unwrapped !is HttpRequestTimeoutException &&
                unwrapped !is ConnectTimeoutException &&
                unwrapped !is SocketTimeoutException
        }
        exponentialDelay()
    }
}

private fun HttpClientConfig<*>.installAuthentication(
    config: NetworkConfig,
    credentialProvider: CredentialProvider,
) {
    install(Auth) {
        // One predicate feeds both gates, so a request either carries the base host's credentials
        // and may be refreshed on 401, or is left alone entirely.
        reAuthorizeOnResponse { response ->
            response.status == HttpStatusCode.Unauthorized &&
                requiresCredentials(response.request.attributes, response.request.url.host, config)
        }
        bearer {
            sendWithoutRequest { requiresCredentials(it.attributes, it.url.host, config) }
            cacheTokens = false
            nonCancellableRefresh = true
            loadTokens {
                credentialProvider.currentCredential()?.let { BearerTokens(it, null) }
            }
            refreshTokens {
                // With Ktor's token cache disabled, oldTokens is reloaded at refresh time rather
                // than necessarily being the credential rejected on the wire.
                val rejected =
                    response.request.headers[HttpHeaders.Authorization]?.removePrefix(BearerPrefix)
                        ?: oldTokens?.accessToken
                val current = credentialProvider.currentCredential()
                if (current != null && current != rejected) {
                    return@refreshTokens BearerTokens(current, null)
                }

                try {
                    when (val result = credentialProvider.refreshCredential(rejected)) {
                        is CredentialRefreshResult.Refreshed ->
                            BearerTokens(result.credential, null)
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

private fun requiresCredentials(attributes: Attributes, host: String, config: NetworkConfig) =
    attributes.contains(RequiresAuth) && host.equals(config.baseUrl.host, ignoreCase = true)

/**
 * The Identity Capability's side of the neutral network inversion (§18.6.1). Foundation network
 * asks for the current credential before an `authenticated()` request and for a refresh after a
 * 401; what a refresh means for the session is the provider's call.
 */
public interface CredentialProvider {
    /** The credential to send now, or null when signed out. Cheap: an in-memory read. */
    public suspend fun currentCredential(): String?

    /** Called once per rejected credential. Identity decides what a rejection means. */
    public suspend fun refreshCredential(rejected: String?): CredentialRefreshResult
}

public sealed interface CredentialRefreshResult {
    public data class Refreshed(public val credential: String) : CredentialRefreshResult

    /** Invalid credentials; the provider has already applied its session semantics. */
    public data object Rejected : CredentialRefreshResult

    /** Refresh could not run right now (offline, server down); the session is unchanged. */
    public data object Unavailable : CredentialRefreshResult
}

public object AnonymousCredentialProvider : CredentialProvider {
    public override suspend fun currentCredential(): String? = null

    public override suspend fun refreshCredential(rejected: String?): CredentialRefreshResult =
        CredentialRefreshResult.Rejected
}

internal const val RequestIdHeader: String = "X-Request-Id"

internal val RequiresAuth = AttributeKey<Unit>("RequiresAuth")
internal val Retryable = AttributeKey<Unit>("Retryable")

/** Opts one request into credentials and a credential-refresh retry. */
public fun HttpRequestBuilder.authenticated() {
    attributes.put(RequiresAuth, Unit)
}

/** Opts an idempotent request into transient transport and response retries. */
public fun HttpRequestBuilder.retryable() {
    attributes.put(Retryable, Unit)
}

private val RequestId =
    createClientPlugin("RequestId") {
        on(SendingRequest) { request, _ ->
            val requestId = Uuid.random().toString()
            currentCoroutineContext()[RequestIdContext]?.latest = requestId
            request.headers[RequestIdHeader] = requestId
        }
    }

internal class RequestIdContext(var latest: String) : AbstractCoroutineContextElement(Key) {
    internal companion object Key : CoroutineContext.Key<RequestIdContext>
}

private const val MaximumRetries = 2
private const val BearerPrefix = "Bearer "
/**
 * Statuses that mean the server itself failed: the client retries idempotent requests on them and
 * `:foundation:resource-runtime` classifies them as `SERVER`.
 */
public val ServerErrorStatuses: IntRange = 500..599
private val IdempotentMethods = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)
