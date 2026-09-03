package dev.mayankmkh.basekmpproject.capability.posts.impl

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.network.tryCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments
import kotlinx.serialization.Serializable

@Serializable
internal data class PostDto(
    val userId: Long,
    val id: Long,
    val title: String,
    val body: String,
)

internal class PostsRemoteSource(private val client: HttpClient) {
    suspend fun getPosts(limit: Int = DEFAULT_LIMIT): Result<List<PostDto>, NetworkFailure> =
        client.tryCatching {
            get {
                    url { appendPathSegments(POSTS_PATH) }
                    parameter("_limit", limit)
                }
                .body()
        }

    suspend fun getPost(id: Long): Result<PostDto, NetworkFailure> = client.tryCatching {
        get { url { appendPathSegments(POSTS_PATH, id.toString()) } }.body()
    }

    private companion object {
        const val POSTS_PATH = "posts"
        const val DEFAULT_LIMIT = 30
    }
}
