package dev.mayankmkh.basekmpproject.capability.todos.impl

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoDraft
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoField
import dev.mayankmkh.basekmpproject.capability.todos.api.TodoViolation
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.bodyOrNull
import dev.mayankmkh.basekmpproject.foundation.network.jsonBody
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.runtime.toResourceProblem
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

/**
 * What a Todos endpoint answered, with the generic [problem] every branch can fall back to when the
 * caller has no domain meaning for it (a 404 on create, a 422 on delete).
 */
internal sealed interface TodoRemoteFailure {
    val problem: ResourceProblem

    data class NotFound(override val problem: ResourceProblem) : TodoRemoteFailure

    data class InvalidInput(
        val violations: List<TodoViolation>,
        override val problem: ResourceProblem,
    ) : TodoRemoteFailure

    data class Infrastructure(override val problem: ResourceProblem) : TodoRemoteFailure
}

internal class TodosRemoteSource(private val client: HttpClient, private val json: Json) {
    suspend fun getTodos(): Result<List<TodoDto>, NetworkFailure> = client.tryCatching {
        get {
                url { appendPathSegments(TODOS_PATH) }
                parameter("userId", DEFAULT_OWNER_ID)
            }
            .body()
    }

    suspend fun getTodo(id: Long): Result<TodoDto, NetworkFailure> = client.tryCatching {
        get { url { appendPathSegments(TODOS_PATH, id.toString()) } }.body()
    }

    suspend fun createTodo(draft: TodoDraft): Result<TodoDto, TodoRemoteFailure> =
        client
            .tryCatching<TodoDto> {
                post {
                        url { appendPathSegments(TODOS_PATH) }
                        jsonBody(CreateTodoDto(userId = draft.ownerId, title = draft.title))
                    }
                    .body()
            }
            .mapError(::toCommandFailure)

    suspend fun setCompleted(
        id: Long,
        completed: Boolean,
    ): Result<TodoDto, TodoRemoteFailure> =
        client
            .tryCatching<TodoDto> {
                patch {
                        url { appendPathSegments(TODOS_PATH, id.toString()) }
                        jsonBody(CompletionDto(completed))
                    }
                    .body()
            }
            .mapError(::toCommandFailure)

    suspend fun renameTodo(id: Long, title: String): Result<TodoDto, TodoRemoteFailure> =
        client
            .tryCatching<TodoDto> {
                patch {
                        url { appendPathSegments(TODOS_PATH, id.toString()) }
                        jsonBody(RenameDto(title))
                    }
                    .body()
            }
            .mapError(::toCommandFailure)

    suspend fun deleteTodo(id: Long): Result<Unit, TodoRemoteFailure> =
        client
            .tryCatching {
                delete { url { appendPathSegments(TODOS_PATH, id.toString()) } }
                Unit
            }
            .mapError(::toCommandFailure)

    private fun toCommandFailure(failure: NetworkFailure): TodoRemoteFailure {
        val problem = failure.toResourceProblem()
        val status = (failure as? NetworkFailure.Http)?.status
        return when (status) {
            HttpStatusCode.NotFound -> TodoRemoteFailure.NotFound(problem)
            HttpStatusCode.UnprocessableEntity ->
                TodoRemoteFailure.InvalidInput(
                    failure
                        .bodyOrNull<ErrorEnvelope>(json)
                        ?.errors
                        .orEmpty()
                        .map(ErrorDto::toViolation),
                    problem,
                )
            else -> TodoRemoteFailure.Infrastructure(problem)
        }
    }

    private companion object {
        const val TODOS_PATH = "todos"
        const val DEFAULT_OWNER_ID = 1L
    }
}

private fun ErrorDto.toViolation() =
    TodoViolation(
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
