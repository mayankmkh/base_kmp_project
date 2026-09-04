# `:foundation:network` design

- **Status:** Accepted 2026-09-04
- **Owner:** mayankmkh@gmail.com
- **Role:** `foundation_runtime`
- **Ktor:** 3.5.2. Where the Ktor prose docs and the Ktor source disagree, this document follows
  the source and says so.

This is the single source for how the HTTP client is assembled. The master document
([`helix-kmp-source-of-truth.md`](helix-kmp-source-of-truth.md) §18.6 and §18.6.1) owns the
ownership split between Foundation network and the Identity Capability; this document owns the
mechanics inside the module.

## 1. Requirements

- Ktor client on Android, iOS, desktop JVM and wasmJs.
- Authenticated requests. The scheme is not final. Bearer is most likely; anything else must be
  addable without touching callers.
- Default headers whose values change at runtime.
- No product vocabulary. Endpoints, DTOs and error-body shapes belong to Capability
  implementations.

## 2. Ownership

| Concern | Owner |
| --- | --- |
| Client assembly, plugin order, engines per target | `:foundation:network` |
| Base URL, timeouts, log level | `:app:*` supplies a `NetworkConfig` |
| Values of changing headers | `:app:*` supplies a `DynamicHeaders` snapshot |
| Current credential and refresh | `:capability:identity-impl` implements `CredentialProvider` |
| Credential persistence | `:capability:identity-impl` over `:platform:secure-storage` |
| Endpoints, DTOs, error-body decoding | Capability implementations |
| Caching, connectivity, request scheduling | `:foundation:resource*`, `:platform:connectivity`; never this module |

Features and Capability APIs never see `HttpClient`. Capability implementations use it directly.
Ktor is not hidden behind an interface: the test seam is `MockEngine`, which runs the real plugin
stack, and a hand-written wrapper would either reproduce Ktor's surface or lose it. The only
abstractions the module owns are the failure model (§8) and the credential contract (§6), both of
which exist so that other modules never import Ktor types for something other than making requests.

## 3. Public surface

```kotlin
public data class NetworkConfig(
    public val baseUrl: Url,                          // path must be empty or end with "/"
    public val requestTimeout: Duration = 30.seconds, // the only portable timeout
    public val connectTimeout: Duration = 10.seconds, // best effort: ignored on Darwin and Js
    public val socketTimeout: Duration = 30.seconds,  // best effort: ignored on Js
    public val logLevel: LogLevel = LogLevel.NONE,    // the app raises it for debug builds
)

/** Header values that may change between requests. Read once per request, must not suspend. */
public fun interface DynamicHeaders {
    public fun current(): Map<String, String>
    public companion object { public val None: DynamicHeaders }
}

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
    /** Refresh could not run right now; the session is unchanged. */
    public data object Unavailable : CredentialRefreshResult
}

public object AnonymousCredentialProvider : CredentialProvider

public fun createHttpClient(
    config: NetworkConfig,
    credentialProvider: CredentialProvider = AnonymousCredentialProvider,
    headers: DynamicHeaders = DynamicHeaders.None,
    clientLogger: Logger = Logger.EMPTY,
    json: Json = createJson(),
): HttpClient

/** Same client on an engine the caller names; tests pass `MockEngine`. */
public fun createHttpClient(engine: HttpClientEngine, /* same parameters */): HttpClient

/** Per-request opt-ins. Both default to off. */
public fun HttpRequestBuilder.authenticated()
public fun HttpRequestBuilder.retryable()

public suspend inline fun <reified T> HttpClient.tryCatching(
    block: suspend HttpClient.() -> T,
): Result<T, NetworkFailure>

public sealed interface NetworkFailure { /* §8 */ }
```

One `HttpClient` per backend. There is no separate anonymous client: a request is anonymous unless
it calls `authenticated()`. `NetworkConfig`, `CredentialProvider` and `DynamicHeaders` are Koin
definitions in the app so a flavour or a test overrides one without rebuilding the stack.

## 4. Engines

Engines are named per target and never resolved off the classpath. JVM default resolution takes
whichever engine the `ServiceLoader` lists first, and the JS/wasm resolution prefers any engine that
is not Js, so an accidental engine dependency in common code changes behaviour silently.

| Target | Engine | Note |
| --- | --- | --- |
| Android | OkHttp | Shared JVM source set with desktop |
| Desktop JVM | OkHttp | Java engine is the alternative if system proxies matter |
| iOS | Darwin | `handleChallenge` is where TLS pinning would go |
| wasmJs | Js | Ships in `ktor-client-core`; the `ktor-client-js` artifact is still declared for clarity |

CIO stays out of `commonMain` and every wasm-reachable source set. In the browser it needs Node
sockets and fails at runtime.

The factory form `HttpClient(EngineFactory) { }` is used so the client owns and closes its engine.
The explicit-engine overload uses `HttpClient(engine) { }`, which leaves the engine to the caller;
that is the right ownership for a `MockEngine` in a test.

**Lifecycle.** The app's Koin definition is a `single` with `onClose { it?.close() }`. Stopping Koin
closes the client, which completes its job and closes the managed engine: OkHttp evicts its
connection pool and shuts its dispatcher down, Darwin invalidates its session. A process that exits
without stopping Koin loses nothing, but tests, desktop restarts and Koin context replacement would
otherwise keep pooled threads and sessions alive. `close()` only initiates shutdown; an owner that
must wait for every resource joins the client's job.

## 5. Requests

**Plugin install order** matters because the first plugin installed into the send pipeline is the
outermost. The module installs, in order: `HttpRequestRetry`, `HttpTimeout`, `ContentNegotiation`,
`Logging`, `Auth`, the request-id plugin, then `defaultRequest`, with `expectSuccess = true`.
Validation always runs outside every plugin, so retry and auth see raw statuses and only the final
response becomes an exception.

**Base URL merging.** `DefaultRequest` merges the configured URL into a request that has no host.
A base path without a trailing slash loses its last segment, and a request path with a leading
slash discards the base path. `NetworkConfig` therefore requires the base path to be empty or end
in `/`. Callers build paths with `url { appendPathSegments("posts", id) }`, which encodes every
segment and cannot produce a leading slash. Absolute URLs bypass merging and go where they say.

**Changing headers** are read from `DynamicHeaders.current()` inside `defaultRequest { }`, whose
block runs once per request. `DefaultRequest` merges with `appendMissing`, so a header set on the
request wins over the default. The snapshot is non-suspending by contract: the app keeps it current
from its own flows, and the transport never waits on a header value.

**Request id.** A tiny `createClientPlugin` on the `SendingRequest` hook sets `X-Request-Id` to a
fresh UUID on every wire attempt, so a retried request is distinguishable server-side. This is the
only custom plugin.

**Redirects.** Ktor's own `HttpRedirect` stays at its defaults: only GET and HEAD are followed, an
HTTPS to HTTP hop is refused, and a 3xx that is not followed surfaces as `RedirectResponseException`
under `expectSuccess`. Ktor drops `Authorization` when the authority changes; the redirected builder
keeps its attributes and re-enters `Auth`, and the base-host predicate (§6) is what stops the token
from being re-added on the foreign host. Each hop runs `SendingRequest`, so each hop carries its own
request id.

On web, every non-standard header costs a CORS preflight and the server must list it in
`Access-Control-Allow-Headers`. Keep the dynamic set small, prefer standard headers such as
`Accept-Language`, and note that browsers refuse to set `User-Agent`, so client identification goes
in a custom header on every target.

## 6. Authentication

Ktor's `Auth` plugin with a `bearer` provider. The credential contract is scheme neutral; the
bearer wiring is one block in one file. A future scheme is an `AuthProvider` implementation plus
one `providers.add` line, and it inherits the same 401 loop and single-flight refresh. The interface
still has an abstract, deprecated `sendWithoutRequest` property that a custom provider must override
with an `error()` getter.

Settings, and why each one is not the default:

| Setting | Value | Reason |
| --- | --- | --- |
| `sendWithoutRequest` | `authenticated()` attribute present and host equals the base host | The prose docs say the default waits for a challenge; the source default is eager. Refresh only fires for requests where this returned true ([KTOR-9732](https://youtrack.jetbrains.com/issue/KTOR-9732)), so it must be true for the API host. The host check also stops a redirect from carrying the token to another host, because `HttpRedirect` sits outside `Auth` and the redirected request re-enters it with the original attributes. |
| `reAuthorizeOnResponse` | status 401 and `authenticated()` attribute present and same host | Public since 3.1, absent from the docs. Without it any 401 enters the refresh loop, including a wrong-password sign-in response. |
| `cacheTokens` | `false` | The provider's in-memory snapshot is already the cache. A second copy is where stale-token bugs after sign-out or account switch come from. `loadTokens` runs per request, and per request it is a memory read. |
| `nonCancellableRefresh` | `true` | Cancelling the request that triggered a refresh must not roll back a refresh that succeeded. Available since 3.4.0. |
| `realm` | `null` | One provider. |

Both gates must carry the attribute check. Gating only `sendWithoutRequest` skips the header but
still lets a 401 on an anonymous request into the refresh loop, because the plugin reads the status
before it consults any provider.

`refreshTokens` first re-reads the provider. If the credential already differs from the one that was
rejected, another caller refreshed it and the request retries with the new value without a second
refresh. Otherwise it calls `refreshCredential(rejected)`. `Refreshed` retries once with the new
credential; `Rejected` and `Unavailable` return the original 401 unchanged; a throwing provider is
treated as `Unavailable`. Ktor serialises concurrent refreshes behind the token holder's lock and
stamps each request with a token version, so two concurrent 401s refresh once.

The refresh call itself, when a real backend exists, goes through the same client without
`authenticated()`. That request never touches the provider or the token lock, and the 3.5.2 bug that
strips a caller-set `Authorization` header from marked refresh requests
([KTOR-9827](https://youtrack.jetbrains.com/issue/KTOR-9827)) does not apply because the request is
not marked. Anonymous requests never contend with an in-flight refresh for the same reason.

The plugin is strictly reactive. Ktor has no proactive refresh before expiry
([KTOR-6419](https://youtrack.jetbrains.com/issue/KTOR-6419)); if Identity wants one, it refreshes
on its own schedule and updates its snapshot.

**Persistence** is Identity's concern and is invisible here. The intended shape for a real backend:
load once at startup into an in-memory snapshot, write through on change, read the snapshot per
request. `currentCredential()` is suspend so the first request can await that initial load rather
than go out without a token. Storage per target: Keystore-wrapped DataStore on Android
(`androidx.security.crypto` is deprecated), Keychain on iOS, the OS credential store via JNA on
desktop, and on web an in-memory access token with the refresh token in an `httpOnly` cookie set by
the backend, since the browser has no secure storage.

## 7. Retry and timeouts

Ktor installs no retry by default, and the transport cannot know whether a call is idempotent or
time sensitive, so retry is opt-in per request with `retryable()`. The policy lives in the module
once: at most two retries, `GET`, `HEAD` and `OPTIONS` only because the plugin does not check the
method itself, on 5xx and 429 or on a transport exception that is neither cancellation nor a
timeout, with Ktor's `exponentialDelay()` which honours the integer-seconds form of `Retry-After`.
Both `retryIf` and `retryOnExceptionIf` are set, because setting only one leaves Ktor's default
`retryOnExceptionOrServerErrors(3)` active for the other path and that default retries everything
including POSTs. The per-request `retry { }` builder is not exposed because it silently resets every
client-level predicate. A timed-out attempt is not retried, matching Ktor's own `retryOnException` default: the caller's
time budget is already spent, and the failure reaches it as `Transport(TIMEOUT)` right away.

Retry is installed before `HttpTimeout` so each attempt gets its own request timeout. Auth's 401
retry is independent of this plugin.

Only `requestTimeout` is a promise: it is enforced in common code on every engine. Connect timeout
is ignored by Darwin and Js, socket timeout by Js. None apply to WebSockets or SSE. A timeout
surfaces as a `CancellationException` wrapping `HttpRequestTimeoutException`; the failure mapper
unwraps it before deciding whether a call was cancelled.

## 8. Failures

`expectSuccess = true` and one mapper. `tryCatching { }` runs a block and returns
`Result<T, NetworkFailure>` from kotlin-result:

```kotlin
public sealed interface NetworkFailure {
    public val cause: Throwable

    /** Non-success status. Body bytes are copied out; decode with [bodyOrNull]. */
    public data class Http(
        public val status: HttpStatusCode,
        public val headers: Headers,
        public val body: ByteArray,
        public val requestId: String?,
        override val cause: ResponseException,
    ) : NetworkFailure

    /** Nothing usable came back. */
    public data class Transport(public val kind: TransportFailureKind, override val cause: Throwable) : NetworkFailure

    /** A success status whose body did not match the requested type. */
    public data class Decoding(override val cause: Throwable) : NetworkFailure

    public data class Unexpected(override val cause: Throwable) : NetworkFailure
}

public enum class TransportFailureKind { OFFLINE, TIMEOUT }

public inline fun <reified T> NetworkFailure.Http.bodyOrNull(json: Json): T?
```

Mapping: `ResponseException` becomes `Http`; `JsonConvertException` and
`NoTransformationFoundException` become `Decoding`; timeout exceptions, after unwrapping, become
`Transport(TIMEOUT)`; `IOException` and unresolved-address failures become `Transport(OFFLINE)`, with
a per-platform hook for engine-specific offline types; genuine cancellation is rethrown; everything
else is `Unexpected`. In Ktor 3.x every non-streaming body is saved and re-readable, so copying the
error body is safe and the live response is not kept.

`bodyOrNull` takes the `Json` explicitly because the failure is detached from the client. Callers
pass the application's shared `Json` single, the same one handed to `createHttpClient`, so error
bodies and success bodies are always decoded under the same rules.

Without `expectSuccess`, an HTML 503 from a gateway surfaces as a transformation exception when a
typed body is requested, which is why status validation runs first.

## 9. Serialization

```kotlin
Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}
```

`ContentNegotiation` adds `Accept` but never `Content-Type`. A request with a body sets
`contentType(ContentType.Application.Json)` explicitly; a `jsonBody(dto)` helper exists so it cannot
be forgotten.

## 10. Logging

The level lives on `NetworkConfig` and defaults to `LogLevel.NONE`. Ktor's own default is `HEADERS`,
which would write every header to whatever the app logs to. `Authorization`, `Cookie` and
`Set-Cookie` are sanitised at every level. Body logging is a debug-only choice because the plugin
buffers bodies to print them. Output goes through Ktor's `Logger` interface to the app's logger.

The app sets `HEADERS` for debug builds and `NONE` otherwise, deciding per target from a runtime
signal rather than a generated `BuildConfig`: the debuggable flag on Android, the debug binary on
iOS, and the absence of the `jpackage.app-path` property on desktop. Web stays at `NONE`; the
browser's Network tab already shows every request and the console belongs to the host page.

## 11. Testing

The engine parameter takes `MockEngine`, which runs on all four targets and exercises the real
pipeline. Dispatch is positional, so per-URL routing lives inside one handler. `MockEngine` has no
SSE capability and its request history is not ordered under concurrency.

Contract tests the module must keep green:

- base URL merge for a relative path and for `appendPathSegments`; an absolute URL is left alone;
- dynamic headers are read per request and a request header wins over a default;
- no `Content-Type` on GET; a fresh `X-Request-Id` on every attempt including retries;
- token attached only with `authenticated()` and only on the base host; foreign host and anonymous
  requests carry no token and a 401 on them never refreshes or retries;
- refresh then retry once; `Rejected`, `Unavailable` and a throwing provider each return the
  original 401 once; two concurrent 401s refresh once; a credential changed by another caller is
  reused without refresh; after sign-out the next request is anonymous with no invalidation call;
- `retryable()` retries a GET 503 with a new request id and does not retry a POST or a
  non-`retryable()` GET; `Retry-After` is honoured;
- failure mapping for 4xx with a body, HTML 503, malformed 2xx JSON, timeout, IO failure,
  cancellation, unexpected exception; the token never appears in the log;
- a same-host redirect keeps the token and a cross-host redirect drops it, each hop with its own
  request id; a POST redirect and an HTTPS downgrade are not followed;
- every target names an engine; closing the Koin context closes the client.

## 12. Decisions and rejected alternatives

- **One client, per-request opt-in for auth and retry.** A second anonymous client was rejected: it
  bought nothing the attribute does not, added lifecycle questions around the shared engine, and
  authenticated-by-default with opt-out lets a wrong-password 401 trigger a refresh and a sign-out.
- **No `AuthScheme` type.** It would map one-to-one onto Ktor's provider config and pre-abstract an
  unknown. Abstract when the second scheme arrives.
- **No custom header plugin.** `defaultRequest` already runs per request and merges correctly.
- **No `HttpApi` wrapper interface.** See §2.
- **No transport-level default retry.** Layers above already retry, and a retrying token endpoint
  would stall every authenticated request behind the token lock.
- **Body bytes, not a live response, in `Http` failures.** Bodies are saved in 3.x anyway, and a
  value can be asserted on and passed around without the call.

### Ktor plugins and options reviewed

Reviewed against Ktor 3.5.2 docs and source on 2026-09-04. Nothing here is installed; the reason is
recorded so the question is not reopened by default.

| Plugin or option | Decision | Reason |
| --- | --- | --- |
| `HttpCache` | Not installed | SQLDelight owns durable caching and account isolation, and `SyncCoordinator` decides when a key is synchronised (§2). Ktor's default cache is unbounded, in memory, and keeps account-scoped bodies. Revisit only as a conditional-request layer under the durable source of truth, see §13. |
| `Resources` | When needed | Typed routes pay off with a large, stable endpoint surface. `appendPathSegments` covers the current one without a generated route model per endpoint. |
| `UserAgent` | Not installed | OkHttp and Darwin already send an identity, browsers refuse to set the header. Client identification goes in a custom header (§5). |
| `ContentEncoding` | Not installed | OkHttp, Darwin and Fetch decompress responses transparently. Install only for request compression a backend measurably wants. |
| `HttpCookies` | Not installed | Bearer auth needs no second credential store. Browser cookies are a Fetch credentials-mode question (§13), not an in-memory jar. |
| `HttpRedirect` | Defaults kept | See §5. Do not relax method checking or the HTTPS downgrade guard globally. |
| `HttpCallValidator`, extra `HttpSend` interceptors | Not added | No transport-wide 2xx error envelope exists; endpoint error semantics belong to Capability implementations. `expectSuccess` plus `tryCatching` is the whole policy. |
| `BodyProgress` | Already installed by Ktor | A capability adds `onUpload`/`onDownload` per request for a large transfer; nothing to configure here. |
| Monitoring observer | When a consumer exists | A dependency-free `HttpSend` observer is cheap once metrics have somewhere to go; a no-op observer is pipeline cost for nothing. OpenTelemetry is not planned. |
| `WebSockets`, `SSE` | When a capability needs one | Every selected engine supports both. Each plugin adds pipeline work; the first consumer installs it and owns the session. |
| `DataConversion`, `Charsets`, `SaveBody`, `PluginsTrace` | Not installed | Defaults already give UTF-8 and repeatable non-streaming bodies; the old save-body plugin is deprecated; the trace plugin is debugger plumbing. |
| Per-platform engine hook | When needed | A narrow, typed hook for a concrete need such as TLS pinning, a proxy or `waitsForConnectivity`. Not a configure-anything escape hatch. |
| `retryOnTimeout`, `modifyRequest` attempt attribute | Not used | Retry stays outside timeout so the time budget is spent once. An attempt attribute only earns its place alongside the monitoring observer. |
| `LoggingFormat.OkHttp` | Not used | Presentation only; it adds no wire fidelity. Level `NONE` and header sanitisation are the policy that matters (§10). |
| `ktor-client-test-base` | Not depended on | Ktor-internal, not published to Maven Central. `MockEngine` history, reusable handlers and `MockEngine.Queue` cover the tests. |

## 13. Open questions

1. Number of backends. One client per base URL; media or analytics hosts get their own, without
   credentials, sharing nothing but the engine factory.
2. Whether a refresh endpoint exists or re-login is the only recovery. Changes Identity, not this
   module.
3. Whether web needs cookies. Fetch credentials mode is configurable on the Js engine; bearer-only
   is the default and the safer CORS story.
4. Whether desktop must honour system proxies. OkHttp needs explicit proxy configuration.
5. Whether `HttpCache` earns a place beneath the SQLDelight source of truth as a conditional-request
   layer (ETag, `If-None-Match`) once a backend sends validators. Two caches disagreeing on what is
   current is the cost.

## 14. References

Ktor 3.5.2 docs: [Auth](https://ktor.io/docs/client-auth.html),
[Bearer](https://ktor.io/docs/client-bearer-auth.html),
[DefaultRequest](https://ktor.io/docs/client-default-request.html),
[Custom plugins](https://ktor.io/docs/client-custom-plugins.html),
[Retry](https://ktor.io/docs/client-request-retry.html),
[Timeout](https://ktor.io/docs/client-timeout.html),
[Logging](https://ktor.io/docs/client-logging.html),
[Response validation](https://ktor.io/docs/client-response-validation.html),
[Engines](https://ktor.io/docs/client-engines.html),
[Testing](https://ktor.io/docs/client-testing.html). Source of record for the settings above:
`ktor-client-auth` `Auth.kt`, `BearerAuthProvider.kt`, `AuthTokenHolder.kt`; `ktor-client-core`
`DefaultRequest.kt`, `HttpRequestRetry.kt`, `HttpTimeout.kt`.
