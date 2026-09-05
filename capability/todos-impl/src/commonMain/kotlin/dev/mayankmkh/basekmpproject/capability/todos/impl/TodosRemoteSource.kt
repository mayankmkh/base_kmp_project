package dev.mayankmkh.basekmpproject.capability.todos.impl

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
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

internal sealed interface TodoReadAnswer {
    data class Found(val todo: TodoDto) : TodoReadAnswer

    data object NotFound : TodoReadAnswer
}

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

    suspend fun getTodo(id: Long): Result<TodoReadAnswer, NetworkFailure> =
        client
            .tryCatching<TodoDto> {
                get { url { appendPathSegments(TODOS_PATH, id.toString()) } }.body()
            }
            .mapAnswer(
                success = { TodoReadAnswer.Found(it) },
                notFound = { TodoReadAnswer.NotFound },
            )

    suspend fun createTodo(draft: TodoDraft): Result<CreateTodoRemoteAnswer, NetworkFailure> =
        client
            .tryCatching<TodoDto> {
                post {
                        url { appendPathSegments(TODOS_PATH) }
                        jsonBody(CreateTodoDto(userId = draft.ownerId, title = draft.title))
                    }
                    .body()
            }
            .fold(
                success = { Ok(CreateTodoRemoteAnswer.Created(it)) },
                failure = { failure ->
                    if (failure.status == HttpStatusCode.UnprocessableEntity) {
                        Ok(CreateTodoRemoteAnswer.InvalidInput(failure.violations()))
                    } else {
                        Err(failure)
                    }
                },
            )

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
            .mapAnswer(
                success = { DeleteTodoRemoteAnswer.Deleted },
                notFound = { DeleteTodoRemoteAnswer.NotFound },
            )

    private fun Result<TodoDto, NetworkFailure>.toUpdateAnswer():
        Result<UpdateTodoRemoteAnswer, NetworkFailure> =
        fold(
            success = { Ok(UpdateTodoRemoteAnswer.Updated(it)) },
            failure = { failure ->
                when (failure.status) {
                    HttpStatusCode.NotFound -> Ok(UpdateTodoRemoteAnswer.NotFound)
                    HttpStatusCode.UnprocessableEntity ->
                        Ok(UpdateTodoRemoteAnswer.InvalidInput(failure.violations()))
                    else -> Err(failure)
                }
            },
        )

    private fun NetworkFailure.violations(): List<Violation<TodoField>> =
        (this as? NetworkFailure.Http)
            ?.bodyOrNull<ErrorEnvelope>(json)
            ?.errors
            .orEmpty()
            .map(ErrorDto::toViolation)

    private companion object {
        const val TODOS_PATH = "todos"
        const val DEFAULT_OWNER_ID = 1L
    }
}

private val NetworkFailure.status: HttpStatusCode?
    get() = (this as? NetworkFailure.Http)?.status

private inline fun <T, R> Result<T, NetworkFailure>.mapAnswer(
    success: (T) -> R,
    notFound: () -> R,
): Result<R, NetworkFailure> =
    fold(
        success = { Ok(success(it)) },
        failure = { failure ->
            if (failure.status == HttpStatusCode.NotFound) Ok(notFound()) else Err(failure)
        },
    )

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
