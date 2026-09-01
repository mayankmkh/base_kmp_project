package dev.mayankmkh.basekmpproject.shared.libs.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ClientTest {

    @Test
    fun `fills in the configured base url for a relative path`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing")

        assertEquals("https://api.example.com/thing", engine.requestHistory.single().url.toString())
    }

    @Test
    fun `leaves a call that names its own host alone`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("https://elsewhere.example.com/thing")

        assertEquals(
            "https://elsewhere.example.com/thing",
            engine.requestHistory.single().url.toString(),
        )
    }

    @Test
    fun `sends the default headers and content type`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing")

        val headers = engine.requestHistory.single().headers
        assertEquals("bkp", headers["X-Client"])
        assertEquals("application/json", headers[HttpHeaders.ContentType])
    }

    @Test
    fun `attaches the token the source holds`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing")

        assertEquals(
            "Bearer first",
            engine.requestHistory.single().headers[HttpHeaders.Authorization],
        )
    }

    @Test
    fun `disableAuthentication keeps the token off the request`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing") { disableAuthentication() }

        assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `refreshes the token after a 401 and retries with the new one`() = runTest {
        val source = FakeBearerTokenSource()
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer first") {
                respond("", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            } else {
                respondOk()
            }
        }

        client(engine, source).get("/thing")

        assertEquals(
            listOf("Bearer first", "Bearer second"),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] },
        )
        assertEquals(1, source.refreshCount)
    }

    @Test
    fun `tells the source it is unauthorized when the refresh call itself is rejected`() = runTest {
        val source = FakeBearerTokenSource(refreshStatus = HttpStatusCode.Unauthorized)
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized, unauthorizedHeaders) }

        // The retry goes out with the token that could not be refreshed, so the call still fails.
        runCatching { client(engine, source).get("/thing") }

        assertTrue(source.refreshUnauthorizedCalled)
    }

    @Test
    fun `the engine overload wires the stack up with no token at all`() = runTest {
        val engine = MockEngine { respondOk() }

        // What a test in another module gets: the real plugin stack, canned responses, and -- via
        // `AnonymousBearerTokenSource` -- an unauthenticated request.
        createHttpClient(engine, config).get("/thing")

        val request = engine.requestHistory.single()
        assertEquals("https://api.example.com/thing", request.url.toString())
        assertEquals("bkp", request.headers["X-Client"])
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `every target ships an engine createHttpClient can find`() {
        // `HttpClient { }` resolves one off the classpath and throws where the source set declares
        // none, which the tests above cannot see -- they hand it a MockEngine.
        createHttpClient(config, FakeBearerTokenSource(), SilentLogger).close()
    }

    private fun client(
        engine: MockEngine,
        source: FakeBearerTokenSource = FakeBearerTokenSource(),
    ) =
        HttpClient(engine) {
            installNetworking(
                config = config,
                bearerTokenSource = source,
                json = createJson(),
                tokenClient = source.tokenClient,
                clientLogger = SilentLogger,
                logLevel = LogLevel.NONE,
            )
        }

    private object SilentLogger : Logger {
        override fun log(message: String) = Unit
    }

    private class FakeBearerTokenSource(refreshStatus: HttpStatusCode = HttpStatusCode.OK) :
        BearerTokenSource {

        private var token = "first"
        var refreshCount = 0
            private set

        var refreshUnauthorizedCalled = false
            private set

        /** Stands in for the auth service the real `refreshToken` would call. */
        val tokenClient =
            HttpClient(MockEngine { respond("", refreshStatus) }) { expectSuccess = true }

        override suspend fun getAuthToken() = token

        override suspend fun getRefreshToken() = "refresh"

        override suspend fun HttpClient.refreshToken() {
            get("https://auth.example.com/token")
            refreshCount++
            token = "second"
        }

        override suspend fun refreshUnauthorized() {
            refreshUnauthorizedCalled = true
        }
    }

    private companion object {
        val config =
            NetworkConfig(
                baseUrl = Url("https://api.example.com"),
                defaultHeaders = mapOf("X-Client" to "bkp"),
            )

        val unauthorizedHeaders = headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
    }
}
