package dev.mayankmkh.basekmpproject.shared.libs.posts

import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.libs.networking.ApiError
import dev.mayankmkh.basekmpproject.shared.libs.networking.disableAuthentication
import dev.mayankmkh.basekmpproject.shared.libs.networking.tryCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * The remote half of the posts data source, against
 * [JSONPlaceholder](https://jsonplaceholder.typicode.com).
 *
 * The local half is `PostsLocalStore` in `:shared:libs:database`; a repository is what pairs them.
 * Nothing here touches the cache, so a caller always knows when it is asking for the network.
 *
 * Every call opts out of the bearer header: the sample endpoint is open, and leaving authentication
 * on would put the token refresh path in front of an anonymous request.
 */
class PostsApi(private val client: HttpClient) {

    suspend fun getPosts(limit: Int = DEFAULT_LIMIT): Result<List<PostDto>, ApiError> =
        client.tryCatching {
            get(POSTS_PATH) {
                    disableAuthentication()
                    // JSONPlaceholder honours `_limit`; without it the feed is 100 posts, more
                    // than a sample screen needs to demonstrate anything.
                    parameter("_limit", limit)
                }
                .body()
        }

    suspend fun getPost(id: String): Result<PostDto, ApiError> = client.tryCatching {
        get("$POSTS_PATH/$id") { disableAuthentication() }.body()
    }

    private companion object {
        const val POSTS_PATH = "posts"
        const val DEFAULT_LIMIT = 30
    }
}
