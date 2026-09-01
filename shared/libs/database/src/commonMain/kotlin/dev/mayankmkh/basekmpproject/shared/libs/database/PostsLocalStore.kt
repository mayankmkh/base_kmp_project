package dev.mayankmkh.basekmpproject.shared.libs.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import dev.mayankmkh.basekmpproject.shared.libs.database.generated.PostsDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The posts table, in terms this project's data layer can use.
 *
 * Exists so features depend on `PostEntity` rather than on SQLDelight's generated types, the same
 * way `:shared:libs:prefs` keeps DataStore out of its callers. It also absorbs the two things that
 * are awkward about the asynchronous query API: `asFlow()` emits the *query*, not its rows, so
 * every observation needs an explicit `awaitAsList`, and a multi-row write has to go through a
 * suspending transaction to land as one table notification instead of N.
 */
class PostsLocalStore(private val provider: PostsDatabaseSource) {

    /**
     * Emits the cached feed, then again after every write to `post`.
     *
     * `flatMapLatest` over a one-shot `flow { emit(database()) }` is what defers opening the
     * database until somebody collects -- building the flow itself stays non-suspending, which is
     * what Store5's `SourceOfTruth.reader` contract requires.
     */
    fun observeAll(): Flow<List<PostEntity>> = withDatabase { database ->
        database.postQueries.selectAll().asFlow().map { query ->
            query.awaitAsList().map { it.toEntity() }
        }
    }

    fun observeById(id: String): Flow<PostEntity?> = withDatabase { database ->
        database.postQueries.selectById(id).asFlow().map { query ->
            query.awaitAsOneOrNull()?.toEntity()
        }
    }

    /** Whether the feed endpoint has completed successfully at least once. */
    fun observeFeedInitialized(): Flow<Boolean> = withDatabase { database ->
        database.postQueries.feedInitializationCount().asFlow().map { query ->
            query.awaitAsOne() > 0L
        }
    }

    suspend fun count(): Long = provider.database().postQueries.countAll().awaitAsOne()

    /**
     * Replaces the whole feed in one transaction.
     *
     * A delete-then-insert rather than an upsert sweep because the server's feed is authoritative:
     * a post that has disappeared upstream should disappear locally too, and `position` has to stay
     * dense for ordering to survive.
     */
    suspend fun replaceAll(posts: List<PostEntity>) {
        val database = provider.database()
        database.transaction {
            database.postQueries.deleteAll()
            posts.forEachIndexed { index, post ->
                database.postQueries.upsert(
                    id = post.id,
                    title = post.title,
                    body = post.body,
                    position = index.toLong(),
                )
            }
            database.postQueries.markFeedInitialized()
        }
    }

    /**
     * Writes a single post without disturbing the feed's ordering.
     *
     * Used by the details screen, which can be the first thing to load a post -- on a cold deep
     * link there is no feed to take a position from, so it appends past the current tail.
     */
    suspend fun upsert(post: PostEntity) {
        val database = provider.database()
        database.transaction {
            val existing = database.postQueries.selectById(post.id).awaitAsOneOrNull()
            val position = existing?.position ?: database.postQueries.countAll().awaitAsOne()
            database.postQueries.upsert(
                id = post.id,
                title = post.title,
                body = post.body,
                position = position,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> withDatabase(block: (PostsDatabase) -> Flow<T>): Flow<T> =
        flow { emit(provider.database()) }.flatMapLatest(block)

    // `Post` is SQLDelight's generated row type for the `post` table, emitted into this package
    // by the `.sq` file's directory.
    private fun Post.toEntity() = PostEntity(id = id, title = title, body = body)
}

/** A cached post, free of both the wire format and SQLDelight's generated row type. */
data class PostEntity(val id: String, val title: String, val body: String)
