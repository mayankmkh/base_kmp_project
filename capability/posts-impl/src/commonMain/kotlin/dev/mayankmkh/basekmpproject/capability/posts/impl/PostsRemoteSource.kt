package dev.mayankmkh.basekmpproject.capability.posts.impl

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import kotlinx.serialization.Serializable

@Serializable
internal data class PostDto(
    val userId: Long,
    val id: Long,
    val title: String,
    val body: String,
)

internal sealed interface PostRemoteAnswer {
    data class Found(val post: PostDto) : PostRemoteAnswer

    data object NotFound : PostRemoteAnswer
}

internal class PostsRemoteSource(private val client: HttpClient) {
    suspend fun getPosts(limit: Int = DEFAULT_LIMIT): Result<List<PostDto>, NetworkFailure> =
        client.tryCatching {
            get {
                    url { appendPathSegments(POSTS_PATH) }
                    parameter("_limit", limit)
                }
                .body()
        }

    suspend fun getPost(id: Long): Result<PostRemoteAnswer, NetworkFailure> =
        client
            .tryCatching<PostDto> {
                get { url { appendPathSegments(POSTS_PATH, id.toString()) } }.body()
            }
            .fold(
                success = { Ok(PostRemoteAnswer.Found(it)) },
                failure = { failure ->
                    if ((failure as? NetworkFailure.Http)?.status == HttpStatusCode.NotFound) {
                        Ok(PostRemoteAnswer.NotFound)
                    } else {
                        Err(failure)
                    }
                },
            )

    private companion object {
        const val POSTS_PATH = "posts"
        const val DEFAULT_LIMIT = 30
    }
}
