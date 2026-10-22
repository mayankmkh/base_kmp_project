package dev.mayankmkh.basekmpproject.capability.todos.impl

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import co.touchlab.kermit.Logger
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.impl.db.AppDatabase
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.preferences.inMemoryPreferenceStores
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.testkit.asProvider
import dev.mayankmkh.basekmpproject.testkit.inMemorySqliteDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class TodosCapabilityImplTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `remote maps 422 errors before infrastructure failure mapping`() = runTest {
        val remote = remote(HttpStatusCode.UnprocessableEntity, ERROR_JSON)

        val answer =
            remote
                .renameTodo(1, "bad")
                .fold(success = { it }, failure = { error("Expected answer, got $it") })

        val invalid = assertIs<UpdateTodoRemoteAnswer.InvalidInput>(answer)
        assertEquals(TodoField.TITLE, invalid.violations.single().field)
        assertEquals("Server says use fewer words.", invalid.violations.single().message)
    }

    @Test
    fun `remote maps 404 to domain not found`() = runTest {
        val remote = remote(HttpStatusCode.NotFound, "{}")

        val answer =
            remote
                .deleteTodo(404)
                .fold(success = { it }, failure = { error("Expected answer, got $it") })

        assertIs<DeleteTodoRemoteAnswer.NotFound>(answer)
    }

    @Test
    fun `client validation returns stable message-free violations without a request`() = runTest {
        val engine = MockEngine { error("Network must not be called") }
        val capability = capability(engine)

        val result = capability.renameTodo(TodoId(1), " ")

        val invalid =
            assertIs<UpdateTodoResult.InvalidInput>(assertIs<Outcome.Completed<*>>(result).value)
        assertEquals("blank", invalid.violations.single().code)
        assertEquals(null, invalid.violations.single().message)
        assertTrue(engine.requestHistory.isEmpty())
        capability.close()
    }

    @Test
    fun `failed optimistic completion restores the local row`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            local.upsert(entity(1, completed = 0))
            val capability =
                capability(
                    MockEngine { respond("down", HttpStatusCode.InternalServerError) },
                    local,
                )

            val result = capability.setCompleted(TodoId(1), completed = true)

            val failed = assertIs<Outcome.Failed>(result)
            assertEquals(ProblemKind.SERVER, failed.problem.kind)
            assertEquals(0, local.find(TodoId(1))?.completed)
            capability.close()
        }

    @Test
    fun `create allocates a local id instead of adopting the fake server id`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            val capability =
                capability(
                    MockEngine {
                        respond(
                            CREATE_JSON,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    local,
                )

            val result = capability.createTodo(TodoDraft("Local todo", ownerId = 7))

            val created =
                assertIs<CreateTodoResult.Created>(assertIs<Outcome.Completed<*>>(result).value)
            assertEquals(TodoId(1_000_000), created.id)
            val stored = local.find(TodoId(1_000_000))
            assertEquals(7, stored?.ownerId)
            assertEquals(1, stored?.localCreated)
            capability.close()
        }

    @Test
    fun `create sends completed explicitly and decodes the echoed todo`() =
        runTest(dispatcher) {
            val sentBodies = mutableListOf<String>()
            // JSONPlaceholder answers a create with the request body plus a new id, so a field the
            // request omits is missing from the response the client then has to decode.
            val engine = MockEngine { request ->
                val sent = request.body.toByteArray().decodeToString().also(sentBodies::add)
                val echoed =
                    JsonObject(
                        Json.parseToJsonElement(sent).jsonObject + ("id" to JsonPrimitive(201))
                    )
                respond(
                    echoed.toString(),
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val capability = capability(engine)

            val result = capability.createTodo(TodoDraft("Echoed todo", ownerId = 1))

            assertTrue(""""completed":false""" in sentBodies.single(), sentBodies.single())
            assertIs<CreateTodoResult.Created>(assertIs<Outcome.Completed<*>>(result).value)
            capability.close()
        }

    @Test
    fun `refresh keeps local creates and replaces server rows`() = runTest {
        val local = createInMemoryTodosLocalSource()
        local.upsert(entity(1, title = "stale"))
        local.upsert(entity(1_000_000, title = "local", localCreated = 1))

        local.replaceFromServer(listOf(entity(1, title = "server"), entity(2)))

        assertEquals("server", local.find(TodoId(1))?.title)
        assertEquals("local", local.find(TodoId(1_000_000))?.title)
    }

    @Test
    fun `a 404 item refresh removes the durable row and completes`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            local.upsert(entity(404))
            val capability =
                capability(MockEngine { respond("{}", HttpStatusCode.NotFound) }, local)

            val outcome = capability.refreshTodo(TodoId(404))

            assertEquals(Outcome.Completed(Unit), outcome)
            assertEquals(null, local.find(TodoId(404)))
            capability.close()
        }

    @Test
    fun `a 404 item refresh keeps a local create and completes`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            val created = entity(1_000_000, localCreated = 1)
            local.upsert(created)
            val capability =
                capability(MockEngine { respond("{}", HttpStatusCode.NotFound) }, local)

            val outcome = capability.refreshTodo(TodoId(1_000_000))

            assertEquals(Outcome.Completed(Unit), outcome)
            assertEquals(created, local.find(TodoId(1_000_000)))
            capability.close()
        }

    @Test
    fun `a delete cancelled before the server answers puts the row back`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            val row = entity(1)
            local.upsert(row)
            val server = GatedServer()
            val capability = capability(server.engine, local)

            val deleting = async { capability.deleteTodo(TodoId(1)) }
            server.entered.await()
            deleting.cancelAndJoin()

            assertEquals(row, local.find(TodoId(1)))
            server.release()
            capability.close()
        }

    @Test
    fun `an update cancelled before the server answers puts the row back`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            val row = entity(1, completed = 0)
            local.upsert(row)
            val server = GatedServer()
            val capability = capability(server.engine, local)

            val completing = async { capability.setCompleted(TodoId(1), completed = true) }
            server.entered.await()
            completing.cancelAndJoin()

            assertEquals(row, local.find(TodoId(1)))
            server.release()
            capability.close()
        }

    /**
     * Neither sync removes a local create, so a create whose caller left before the server answered
     * would otherwise stay behind as a row nothing confirms and nothing reconciles.
     */
    @Test
    fun `a create cancelled before the server answers leaves no row`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            val server = GatedServer()
            val capability = capability(server.engine, local)

            val creating = async { capability.createTodo(TodoDraft("Unconfirmed", ownerId = 7)) }
            server.entered.await()
            creating.cancelAndJoin()

            assertEquals(emptyList(), local.observeTodos(TodoSettings()).first())
            server.release()
            capability.close()
        }

    /**
     * A cancellation can arrive while the optimistic write is still on the database lane: the row
     * is committed, yet the write's own `withContext` hands the cancellation back instead of
     * returning. The revert must still cover that row, or a create stays behind and a delete stays
     * applied.
     */
    @Test
    fun `a cancellation landing with the optimistic write still reverts it`() =
        runTest(dispatcher) {
            val driver = CancellingDriver(inMemorySqliteDriver(AppDatabase.Schema))
            val local = TodosLocalSource(driver.asProvider())
            val row = entity(1)
            local.upsert(row)
            val server = GatedServer()
            val capability = capability(server.engine, local)

            lateinit var creating: Job
            driver.onNextWrite = { creating.cancel() }
            async {
                creating = coroutineContext.job
                capability.createTodo(TodoDraft("Unconfirmed", ownerId = 7))
            }
                .join()

            lateinit var deleting: Job
            driver.onNextWrite = { deleting.cancel() }
            async {
                deleting = coroutineContext.job
                capability.deleteTodo(TodoId(1))
            }
                .join()

            assertTrue(creating.isCancelled)
            assertTrue(deleting.isCancelled)
            assertEquals(listOf(row), local.observeTodos(TodoSettings()).first())
            server.release()
            capability.close()
        }

    /**
     * A revert that cannot run, such as one reaching a driver already closed at shutdown, must not
     * replace the cancellation: the caller's scope is ending, and the failure travels with it.
     */
    @Test
    fun `a revert that fails keeps the cancellation and carries the failure`() = runTest {
        val driver = inMemorySqliteDriver(AppDatabase.Schema)
        val local = TodosLocalSource(driver.asProvider())
        local.upsert(entity(1))
        driver.close()
        val cancellation = CancellationException("caller left")

        val thrown =
            assertFailsWith<CancellationException> {
                local.revertingOnCancel(TodoId(1), expected = null, previous = entity(1)) {
                    throw cancellation
                }
            }

        assertSame(cancellation, thrown)
        assertEquals(1, thrown.suppressed.size)
    }

    @Test
    fun `a cancelled update leaves a newer edit alone`() =
        runTest(dispatcher) {
            val local = createInMemoryTodosLocalSource()
            local.upsert(entity(1, title = "before"))
            val server = GatedServer()
            val capability = capability(server.engine, local)

            val renaming = async { capability.renameTodo(TodoId(1), "optimistic") }
            server.entered.await()
            local.upsert(entity(1, title = "newer"))
            renaming.cancelAndJoin()

            assertEquals("newer", local.find(TodoId(1))?.title)
            server.release()
            capability.close()
        }

    @Test
    fun `settings are applied by SQL queries`() = runTest {
        val local = createInMemoryTodosLocalSource()
        local.replaceFromServer(
            listOf(
                entity(1, title = "Zulu", completed = 0),
                entity(2, title = "Alpha", completed = 1),
                entity(3, title = "Beta", completed = 0),
            )
        )

        val visible =
            local.observeTodos(TodoSettings(hideCompleted = true, sort = TodoSort.TITLE)).first()

        assertEquals(listOf("Beta", "Zulu"), visible.map { it.title })
    }

    @Test
    fun `settings source round trips the capability value`() = runTest {
        val source = TodosSettingsSource(inMemoryPreferenceStores())
        val expected = TodoSettings(hideCompleted = true, sort = TodoSort.TITLE)

        source.update(expected)

        assertEquals(expected, source.observe().first())
    }

    @Test
    fun `updating one setting keeps the rest of the stored document`() = runTest {
        val source = TodosSettingsSource(inMemoryPreferenceStores())
        source.update(TodoSettings(hideCompleted = true, sort = TodoSort.TITLE))

        source.update(source.observe().first().copy(hideCompleted = false))

        assertEquals(
            TodoSettings(hideCompleted = false, sort = TodoSort.TITLE),
            source.observe().first(),
        )
    }

    private fun capability(
        engine: MockEngine,
        local: TodosLocalSource = createInMemoryTodosLocalSource(),
    ): TodosCapabilityImpl =
        TodosCapabilityImpl(
            remoteSource = TodosRemoteSource(createHttpClient(engine, CONFIG), JSON),
            localSource = local,
            settingsSource = TodosSettingsSource(inMemoryPreferenceStores()),
            applicationRuntimeScope =
                ApplicationRuntimeScope(
                    dispatcher,
                    CoroutineExceptionHandler { _, throwable -> throw throwable },
                ),
            connectivityMonitor = ConnectivityMonitor { MutableStateFlow(true) },
            logger = Logger,
        )

    private fun remote(status: HttpStatusCode, body: String): TodosRemoteSource =
        TodosRemoteSource(
            createHttpClient(
                MockEngine {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                CONFIG,
            ),
            JSON,
        )

    /** A server that holds every request until [release], so a caller can be cancelled mid-call. */
    private class GatedServer {
        val entered = CompletableDeferred<Unit>()
        private val gate = CompletableDeferred<Unit>()
        val engine = MockEngine {
            entered.complete(Unit)
            gate.await()
            respond("down", HttpStatusCode.InternalServerError)
        }

        fun release() {
            gate.complete(Unit)
        }
    }

    /** Runs [onNextWrite] once, right after the next write statement has committed. */
    private class CancellingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        var onNextWrite: (() -> Unit)? = null

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            val result = delegate.execute(identifier, sql, parameters, binders)
            onNextWrite?.also { onNextWrite = null }?.invoke()
            return result
        }
    }

    private fun entity(
        id: Long,
        title: String = "Todo $id",
        completed: Long = 0,
        localCreated: Long = 0,
    ) = TodoEntity(id, ownerId = 1, title, completed, localCreated)

    private companion object {
        val CONFIG = NetworkConfig(Url("https://jsonplaceholder.typicode.com"))
        val JSON = Json { ignoreUnknownKeys = true }
        const val ERROR_JSON =
            """{"errors":[{"field":"title","code":"too_long","message":"Server says use fewer words."}]}"""
        const val CREATE_JSON = """{"userId":7,"id":201,"title":"Local todo","completed":false}"""
    }
}
