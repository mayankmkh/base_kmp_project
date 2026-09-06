package dev.mayankmkh.basekmpproject.capability.todos.impl

import co.touchlab.kermit.Logger
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.capability.todos.api.CreateTodoResult
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoId
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSettings
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoSort
import dev.mayankmkh.basekmpproject.capability.todos.api.UpdateTodoResult
import dev.mayankmkh.basekmpproject.foundation.network.NetworkConfig
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import dev.mayankmkh.basekmpproject.foundation.preferences.inMemoryPreferenceStores
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.runtime.ApplicationRuntimeScope
import dev.mayankmkh.basekmpproject.platform.connectivity.ConnectivityMonitor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

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
