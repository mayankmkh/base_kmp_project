package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

class ClientTest {
    @Test
    fun `merges relative and segment paths while leaving absolute urls alone`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = client(engine, config = NetworkConfig(Url("https://api.example.com/root/")))

        client.get("relative")
        client.get { url { appendPathSegments("posts", "1") } }
        client.get("https://elsewhere.example.com/thing")

        assertEquals(
            listOf(
                "https://api.example.com/root/relative",
                "https://api.example.com/root/posts/1",
                "https://elsewhere.example.com/thing",
            ),
            engine.requestHistory.map { it.url.toString() },
        )
    }

    @Test
    fun `rejects a base path without a trailing slash`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                NetworkConfig(Url("https://api.example.com/root"))
            }

        assertContains(error.message.orEmpty(), "empty or end with '/'")
    }

    @Test
    fun `reads dynamic headers per request and keeps a request override`() = runTest {
        var value = "first"
        var reads = 0
        val engine = MockEngine { respondOk() }
        val client =
            client(
                engine,
                headers =
                    DynamicHeaders {
                        reads++
                        mapOf("X-Dynamic" to value)
                    },
            )

        client.get("one")
        value = "second"
        client.get("two") { header("X-Dynamic", "request") }

        assertEquals("first", engine.requestHistory[0].headers["X-Dynamic"])
        assertEquals("request", engine.requestHistory[1].headers["X-Dynamic"])
        assertEquals(2, reads)
    }

    @Test
    fun `get has no content type and jsonBody sets one for a body`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = client(engine)

        client.get("thing")
        client.post("thing") { jsonBody(Payload("value")) }

        assertNull(engine.requestHistory[0].headers[HttpHeaders.ContentType])
        assertEquals(
            ContentType.Application.Json.toString(),
            engine.requestHistory[1].body.contentType?.toString(),
        )
    }

    @Test
    fun `retry gives every wire attempt a fresh request id`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            if (attempts == 1) respond("retry", HttpStatusCode.ServiceUnavailable) else respondOk()
        }

        client(engine).get("thing") { retryable() }

        val requestIds = engine.requestHistory.map { it.headers[RequestIdHeader] }
        assertEquals(2, requestIds.size)
        assertNotNull(requestIds[0])
        assertNotNull(requestIds[1])
        assertNotEquals(requestIds[0], requestIds[1])
    }

    @Test
    fun `token is attached only to authenticated base-host requests`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = client(engine)

        client.get("authenticated") { authenticated() }
        client.get("anonymous")
        client.get("https://elsewhere.example.com/foreign") { authenticated() }

        assertEquals(
            listOf("Bearer first", null, null),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] },
        )
    }

    @Test
    fun `redirect keeps the token on the base host and drops it across hosts`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/moved" -> redirectTo("https://api.example.com/here")
                "/leaving" -> redirectTo("https://elsewhere.example.com/there")
                else -> respondOk()
            }
        }
        val client = client(engine)

        client.get("moved") { authenticated() }
        client.get("leaving") { authenticated() }

        val hops = engine.requestHistory
        assertEquals(
            listOf("/moved", "/here", "/leaving", "/there"),
            hops.map { it.url.encodedPath },
        )
        assertEquals(
            listOf("Bearer first", "Bearer first", "Bearer first", null),
            hops.map { it.headers[HttpHeaders.Authorization] },
        )
        // Every hop is its own wire attempt and gets its own id.
        assertEquals(hops.size, hops.mapNotNull { it.headers[RequestIdHeader] }.toSet().size)
    }

    @Test
    fun `redirect is not followed for a post or onto plain http`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/post" -> redirectTo("https://api.example.com/elsewhere")
                "/downgrade" -> redirectTo("http://api.example.com/insecure")
                else -> respondOk()
            }
        }
        val client = client(engine)

        assertFailsWith<RedirectResponseException> { client.post("post") }
        assertFailsWith<RedirectResponseException> { client.get("downgrade") }

        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun `anonymous and foreign 401 responses never refresh or retry`() = runTest {
        val provider = FakeCredentialProvider()
        val engine = MockEngine {
            respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }
        val client = client(engine, provider)

        assertFailsWith<ClientRequestException> { client.get("anonymous") }
        assertFailsWith<ClientRequestException> {
            client.get("https://elsewhere.example.com/foreign") { authenticated() }
        }

        assertEquals(2, engine.requestHistory.size)
        assertEquals(0, provider.refreshCount)
    }

    @Test
    fun `401 refreshes and retries once with the new credential`() = runTest {
        val provider = FakeCredentialProvider()
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer first") {
                respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            } else {
                respondOk()
            }
        }

        client(engine, provider).get("thing") { authenticated() }

        assertEquals(
            listOf("Bearer first", "Bearer second"),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] },
        )
        assertEquals(listOf<String?>("first"), provider.rejectedCredentials)
    }

    @Test
    fun `failed refresh outcomes preserve the original 401 without retry`() = runTest {
        val outcomes =
            listOf<suspend FakeCredentialProvider.() -> CredentialRefreshResult>(
                { CredentialRefreshResult.Rejected },
                { CredentialRefreshResult.Unavailable },
                { throw IllegalStateException("refresh failed") },
            )

        outcomes.forEach { refreshAction ->
            val provider = FakeCredentialProvider(refreshAction)
            val engine = MockEngine {
                respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            }

            val error =
                assertFailsWith<ClientRequestException> {
                    client(engine, provider).get("thing") { authenticated() }
                }

            assertEquals(HttpStatusCode.Unauthorized, error.response.status)
            assertEquals(1, engine.requestHistory.size)
            assertEquals(1, provider.refreshCount)
        }
    }

    @Test
    fun `two concurrent 401 responses refresh once`() = runTest {
        val bothUnauthorized = CompletableDeferred<Unit>()
        var unauthorizedCount = 0
        val provider = FakeCredentialProvider {
            bothUnauthorized.await()
            token = "second"
            CredentialRefreshResult.Refreshed("second")
        }
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer first") {
                unauthorizedCount++
                if (unauthorizedCount == 2) bothUnauthorized.complete(Unit)
                respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            } else {
                respondOk()
            }
        }
        val client = client(engine, provider)

        awaitAll(
            async { client.get("one") { authenticated() } },
            async { client.get("two") { authenticated() } },
        )

        assertEquals(1, provider.refreshCount)
        assertEquals(
            2,
            engine.requestHistory.count {
                it.headers[HttpHeaders.Authorization] == "Bearer second"
            },
        )
    }

    @Test
    fun `credential changed by another caller is reused without refresh`() = runTest {
        val provider = ChangingCredentialProvider()
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer first") {
                respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            } else {
                respondOk()
            }
        }

        client(engine, provider).get("thing") { authenticated() }

        assertEquals(
            listOf("Bearer first", "Bearer second"),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] },
        )
        assertEquals(0, provider.refreshCount)
    }

    @Test
    fun `sign out makes the next authenticated request anonymous without invalidation`() = runTest {
        val provider = FakeCredentialProvider()
        val engine = MockEngine { respondOk() }
        val client = client(engine, provider)

        client.get("before") { authenticated() }
        provider.token = null
        client.get("after") { authenticated() }

        assertEquals(
            listOf("Bearer first", null),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] },
        )
        assertEquals(0, provider.refreshCount)
    }

    @Test
    fun `retry is opt in and limited to idempotent methods`() = runTest {
        val attempts = mutableMapOf<String, Int>()
        val engine = MockEngine { request ->
            val key = "${request.method.value}:${request.url.encodedPath}"
            val count = attempts.getOrElse(key) { 0 } + 1
            attempts[key] = count
            if (count == 1) respond("retry", HttpStatusCode.ServiceUnavailable) else respondOk()
        }
        val client = client(engine)

        client.get("retry") { retryable() }
        assertFailsWith<ServerResponseException> { client.post("post") { retryable() } }
        assertFailsWith<ServerResponseException> { client.get("plain") }

        assertEquals(2, attempts["GET:/retry"])
        assertEquals(1, attempts["POST:/post"])
        assertEquals(1, attempts["GET:/plain"])
    }

    @Test
    fun `retryable get retries a transport exception but not a timeout`() = runTest {
        var ioAttempts = 0
        val ioEngine = MockEngine {
            ioAttempts++
            if (ioAttempts == 1) throw IOException("offline") else respondOk()
        }

        client(ioEngine).get("io") { retryable() }

        assertEquals(2, ioAttempts)

        var timeoutAttempts = 0
        val timeoutEngine = MockEngine {
            timeoutAttempts++
            throw HttpRequestTimeoutException(HttpRequestBuilder())
        }

        assertFailsWith<HttpRequestTimeoutException> {
            client(timeoutEngine).get("timeout") { retryable() }
        }
        assertEquals(1, timeoutAttempts)
    }

    @Test
    fun `retry after seconds delays the retry`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            if (attempts == 1) {
                respond(
                    "slow down",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.RetryAfter, "2"),
                )
            } else {
                respondOk()
            }
        }

        client(engine).get("thing") { retryable() }

        assertEquals(2, attempts)
        assertTrue(currentTime >= 2_000)
    }

    @Test
    fun `sensitive headers never expose the token in logs`() = runTest {
        val log = StringBuilder()
        val logger =
            object : Logger {
                override fun log(message: String) {
                    log.appendLine(message)
                }
            }
        val engine = MockEngine {
            respond(
                "",
                headers = headersOf(HttpHeaders.SetCookie, "session=response-secret"),
            )
        }

        createHttpClient(
                engine = engine,
                config = config.copy(logLevel = LogLevel.HEADERS),
                credentialProvider = FakeCredentialProvider(),
                clientLogger = logger,
            )
            .get("thing") {
                authenticated()
                header(HttpHeaders.Cookie, "session=request-secret")
            }

        assertContains(log.toString(), HttpHeaders.Authorization)
        assertFalse("first" in log.toString(), "credential leaked into the log:\n$log")
        assertFalse("request-secret" in log.toString(), "cookie leaked into the log:\n$log")
        assertFalse("response-secret" in log.toString(), "set-cookie leaked into the log:\n$log")
    }

    @Test
    fun `engine overload has the anonymous stack and every target names a default engine`() =
        runTest {
            val engine = MockEngine { respondOk() }

            createHttpClient(engine, config).get("thing")

            assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
            createHttpClient(config).close()
        }

    private fun client(
        engine: MockEngine,
        provider: CredentialProvider = FakeCredentialProvider(),
        headers: DynamicHeaders = DynamicHeaders.None,
        config: NetworkConfig = Companion.config,
    ) =
        createHttpClient(
            engine = engine,
            config = config,
            credentialProvider = provider,
            headers = headers,
        )

    private fun MockRequestHandleScope.redirectTo(location: String) =
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))

    private class FakeCredentialProvider(
        private val refreshAction: suspend FakeCredentialProvider.() -> CredentialRefreshResult = {
            token = "second"
            CredentialRefreshResult.Refreshed("second")
        }
    ) : CredentialProvider {
        var token: String? = "first"
        var refreshCount = 0
            private set

        val rejectedCredentials = mutableListOf<String?>()

        override suspend fun currentCredential(): String? = token

        override suspend fun refreshCredential(rejected: String?): CredentialRefreshResult {
            refreshCount++
            rejectedCredentials += rejected
            return refreshAction()
        }
    }

    private class ChangingCredentialProvider : CredentialProvider {
        private var reads = 0
        var refreshCount = 0
            private set

        override suspend fun currentCredential(): String = if (reads++ == 0) "first" else "second"

        override suspend fun refreshCredential(rejected: String?): CredentialRefreshResult {
            refreshCount++
            return CredentialRefreshResult.Unavailable
        }
    }

    @Serializable private data class Payload(val value: String)

    private companion object {
        val config = NetworkConfig(baseUrl = Url("https://api.example.com"))
        val unauthorizedHeaders = headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
    }
}
