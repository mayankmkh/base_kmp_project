package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    fun `sends default headers without a content type on get`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing")

        val headers = engine.requestHistory.single().headers
        assertEquals("bkp", headers["X-Client"])
        assertNull(headers[HttpHeaders.ContentType])
    }

    @Test
    fun `adds a unique request id to every request`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = client(engine)

        client.get("/one")
        client.get("/two")

        val requestIds = engine.requestHistory.map { it.headers["X-Request-Id"] }
        assertNotNull(requestIds[0])
        assertNotNull(requestIds[1])
        assertNotEquals(requestIds[0], requestIds[1])
    }

    @Test
    fun `attaches the token to a base host request`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing")

        assertEquals(
            "Bearer first",
            engine.requestHistory.single().headers[HttpHeaders.Authorization],
        )
    }

    @Test
    fun `does not attach the token to a foreign host request`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("https://elsewhere.example.com/thing")

        assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `disableAuthentication keeps the token off the request`() = runTest {
        val engine = MockEngine { respondOk() }

        client(engine).get("/thing") { disableAuthentication() }

        assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `refreshes the token after a 401 and retries with the new one`() = runTest {
        val provider = FakeCredentialProvider()
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer first") {
                respond("", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            } else {
                respondOk()
            }
        }

        client(engine, provider).get("/thing")

        assertEquals(
            listOf("Bearer first", "Bearer second"),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] },
        )
        assertEquals(1, provider.refreshCount)
    }

    @Test
    fun `rejected refresh surfaces the original 401 without retrying`() = runTest {
        val provider = FakeCredentialProvider { CredentialRefreshResult.Rejected }
        val engine = MockEngine {
            respond("rejected", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }

        val error =
            assertFailsWith<ClientRequestException> { client(engine, provider).get("/thing") }

        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(1, provider.refreshCount)
    }

    @Test
    fun `unavailable refresh surfaces the original 401 without retrying`() = runTest {
        val provider = FakeCredentialProvider { CredentialRefreshResult.Unavailable }
        val engine = MockEngine {
            respond("unavailable", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }

        val error =
            assertFailsWith<ClientRequestException> { client(engine, provider).get("/thing") }

        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(1, provider.refreshCount)
    }

    @Test
    fun `provider failure is unavailable and does not retry`() = runTest {
        val provider = FakeCredentialProvider {
            throw IllegalStateException("refresh transport failed")
        }
        val engine = MockEngine {
            respond("unavailable", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }

        val error =
            assertFailsWith<ClientRequestException> { client(engine, provider).get("/thing") }

        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(1, provider.refreshCount)
    }

    @Test
    fun `no auth request 401 never refreshes or retries`() = runTest {
        val provider = FakeCredentialProvider()
        val engine = MockEngine {
            respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }

        val error =
            assertFailsWith<ClientRequestException> {
                client(engine, provider).get("/thing") { disableAuthentication() }
            }

        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(0, provider.refreshCount)
    }

    @Test
    fun `foreign host 401 never refreshes or retries`() = runTest {
        val provider = FakeCredentialProvider()
        val engine = MockEngine {
            respond("unauthorized", HttpStatusCode.Unauthorized, unauthorizedHeaders)
        }

        val error =
            assertFailsWith<ClientRequestException> {
                client(engine, provider).get("https://elsewhere.example.com/thing")
            }

        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(0, provider.refreshCount)
    }

    @Test
    fun `two concurrent 401s refresh once`() = runTest {
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
                respond("", HttpStatusCode.Unauthorized, unauthorizedHeaders)
            } else {
                respondOk()
            }
        }
        val client = client(engine, provider)

        awaitAll(async { client.get("/one") }, async { client.get("/two") })

        assertEquals(1, provider.refreshCount)
        assertEquals(
            2,
            engine.requestHistory.count { request ->
                request.headers[HttpHeaders.Authorization] == "Bearer second"
            },
        )
    }

    @Test
    fun `the engine overload wires the anonymous stack`() = runTest {
        val engine = MockEngine { respondOk() }

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
        createHttpClient(config, FakeCredentialProvider(), SilentLogger).close()
    }

    private fun client(
        engine: MockEngine,
        provider: FakeCredentialProvider = FakeCredentialProvider(),
    ) = createHttpClient(engine, config, provider, SilentLogger)

    private object SilentLogger : Logger {
        override fun log(message: String) = Unit
    }

    private class FakeCredentialProvider(
        private val refreshAction: suspend FakeCredentialProvider.() -> CredentialRefreshResult = {
            token = "second"
            CredentialRefreshResult.Refreshed("second")
        }
    ) : CredentialProvider {
        var token: String? = "first"
        var refreshCount = 0
            private set

        override suspend fun currentBearerToken(): String? = token

        override suspend fun refreshBearerToken(): CredentialRefreshResult {
            refreshCount++
            return refreshAction()
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
