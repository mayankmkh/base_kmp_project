package dev.mayankmkh.basekmpproject.capability.todos.impl

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.answerOn
import dev.mayankmkh.basekmpproject.foundation.network.bodyOrNull
import dev.mayankmkh.basekmpproject.foundation.network.jsonBody
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class TodoDto(
    val userId: Long,
    val id: Long,
    val title: String,
    val completed: Boolean,
)

@Serializable
private data class CreateTodoDto(
    val userId: Long,
    val title: String,
    val completed: Boolean = false,
)

@Serializable private data class CompletionDto(val completed: Boolean)

@Serializable private data class RenameDto(val title: String)

@Serializable private data class ErrorEnvelope(val errors: List<ErrorDto> = emptyList())

@Serializable
private data class ErrorDto(
    val field: String? = null,
    val code: String,
    val message: String? = null,
)

internal sealed interface CreateTodoRemoteAnswer {
    data class Created(val todo: TodoDto) : CreateTodoRemoteAnswer

    data class InvalidInput(val violations: List<Violation<TodoField>>) : CreateTodoRemoteAnswer
}

internal sealed interface UpdateTodoRemoteAnswer {
    data class Updated(val todo: TodoDto) : UpdateTodoRemoteAnswer

    data object NotFound : UpdateTodoRemoteAnswer

    data class InvalidInput(val violations: List<Violation<TodoField>>) : UpdateTodoRemoteAnswer
}

internal sealed interface DeleteTodoRemoteAnswer {
    data object Deleted : DeleteTodoRemoteAnswer

    data object NotFound : DeleteTodoRemoteAnswer
}

internal class TodosRemoteSource(private val client: HttpClient, private val json: Json) {
    suspend fun getTodos(): Result<List<TodoDto>, NetworkFailure> = client.tryCatching {
        get {
                url { appendPathSegments(TODOS_PATH) }
                parameter("userId", DEFAULT_OWNER_ID)
            }
            .body()
    }

    /** The todo, or null when the server answers that it no longer exists. */
    suspend fun getTodo(id: Long): Result<TodoDto?, NetworkFailure> =
        client
            .tryCatching<TodoDto?> {
                get { url { appendPathSegments(TODOS_PATH, id.toString()) } }.body<TodoDto>()
            }
            .answerOn(HttpStatusCode.NotFound) { null }

    suspend fun createTodo(draft: TodoDraft): Result<CreateTodoRemoteAnswer, NetworkFailure> =
        client
            .tryCatching<TodoDto> {
                post {
                        url { appendPathSegments(TODOS_PATH) }
                        jsonBody(CreateTodoDto(userId = draft.ownerId, title = draft.title))
                    }
                    .body()
            }
            .map<TodoDto, NetworkFailure, CreateTodoRemoteAnswer> {
                CreateTodoRemoteAnswer.Created(it)
            }
            .answerOn(HttpStatusCode.UnprocessableEntity) {
                CreateTodoRemoteAnswer.InvalidInput(it.violations())
            }

    suspend fun setCompleted(
        id: Long,
        completed: Boolean,
    ): Result<UpdateTodoRemoteAnswer, NetworkFailure> =
        client
            .tryCatching<TodoDto> {
                patch {
                        url { appendPathSegments(TODOS_PATH, id.toString()) }
                        jsonBody(CompletionDto(completed))
                    }
                    .body()
            }
            .toUpdateAnswer()

    suspend fun renameTodo(
        id: Long,
        title: String,
    ): Result<UpdateTodoRemoteAnswer, NetworkFailure> =
        client
            .tryCatching<TodoDto> {
                patch {
                        url { appendPathSegments(TODOS_PATH, id.toString()) }
                        jsonBody(RenameDto(title))
                    }
                    .body()
            }
            .toUpdateAnswer()

    suspend fun deleteTodo(id: Long): Result<DeleteTodoRemoteAnswer, NetworkFailure> =
        client
            .tryCatching {
                delete { url { appendPathSegments(TODOS_PATH, id.toString()) } }
                Unit
            }
            .map<Unit, NetworkFailure, DeleteTodoRemoteAnswer> { DeleteTodoRemoteAnswer.Deleted }
            .answerOn(HttpStatusCode.NotFound) { DeleteTodoRemoteAnswer.NotFound }

    private fun Result<TodoDto, NetworkFailure>.toUpdateAnswer():
        Result<UpdateTodoRemoteAnswer, NetworkFailure> =
        map<TodoDto, NetworkFailure, UpdateTodoRemoteAnswer> { UpdateTodoRemoteAnswer.Updated(it) }
            .answerOn(HttpStatusCode.NotFound) { UpdateTodoRemoteAnswer.NotFound }
            .answerOn(HttpStatusCode.UnprocessableEntity) {
                UpdateTodoRemoteAnswer.InvalidInput(it.violations())
            }

    private fun NetworkFailure.Http.violations(): List<Violation<TodoField>> =
        bodyOrNull<ErrorEnvelope>(json)?.errors.orEmpty().map(ErrorDto::toViolation)

    private companion object {
        const val TODOS_PATH = "todos"
        const val DEFAULT_OWNER_ID = 1L
    }
}

private fun ErrorDto.toViolation() =
    Violation(
        field =
            when (field) {
                "title" -> TodoField.TITLE
                "ownerId",
                "userId" -> TodoField.OWNER_ID
                else -> null
            },
        code = code,
        message = message,
    )
