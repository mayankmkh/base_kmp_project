package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import dev.mayankmkh.basekmpproject.capability.posts.impl.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The posts table in capability-owned terms.
 *
 * Keeps SQLDelight's generated types inside this local source so the rest of the capability works
 * with `PostEntity`. It also absorbs the two things that are awkward about the asynchronous query
 * API: `asFlow()` emits the *query*, not its rows, so every observation needs an explicit
 * `awaitAsList`, and a multi-row write has to go through a suspending transaction to land as one
 * table notification instead of N. Feed and detail writes share each entity row, so the last
 * committed write wins when they overlap.
 */
internal class PostsLocalSource(private val provider: PostsDatabaseProvider) {

    /**
     * Emits the durable feed, then again after every write to `post` or `postFeedEntry`.
     *
     * `flatMapLatest` over a one-shot `flow { emit(database()) }` is what defers opening the
     * database until somebody collects. Building the flow itself stays non-suspending, which keeps
     * coordinator-backed observation cold.
     */
    fun observeFeed(): Flow<List<PostEntity>> = withDatabase { database ->
        database.postsSchemaQueries.selectFeed(::PostEntity).asFlow().map { query ->
            query.awaitAsList()
        }
    }

    fun observeById(id: String): Flow<PostEntity?> = withDatabase { database ->
        database.postsSchemaQueries.selectById(id, ::PostEntity).asFlow().map { query ->
            query.awaitAsOneOrNull()
        }
    }

    /** Whether the feed endpoint has completed successfully at least once. */
    fun observeFeedInitialized(): Flow<Boolean> = withDatabase { database ->
        database.postsSchemaQueries
            .feedInitializationCount()
            .asFlow()
            .map { query -> query.awaitAsOne() > 0L }
            .distinctUntilChanged()
    }

    suspend fun count(): Long = provider.database().postsSchemaQueries.countAll().awaitAsOne()

    /** Replaces feed membership and order in one transaction without deleting entity rows. */
    suspend fun replaceFeed(posts: List<PostEntity>) {
        val database = provider.database()
        val distinctPosts = posts.distinctBy { it.id }
        database.transaction {
            distinctPosts.forEach { post ->
                database.postsSchemaQueries.upsert(
                    id = post.id,
                    author_id = post.authorId,
                    title = post.title,
                    body = post.body,
                )
            }
            database.postsSchemaQueries.deleteFeedEntries()
            distinctPosts.forEachIndexed { index, post ->
                database.postsSchemaQueries.insertFeedEntry(post.id, index.toLong())
            }
            database.postsSchemaQueries.markFeedInitialized()
        }
    }

    /** Writes a single post without changing feed membership or order. */
    suspend fun upsert(post: PostEntity) {
        provider
            .database()
            .postsSchemaQueries
            .upsert(
                id = post.id,
                author_id = post.authorId,
                title = post.title,
                body = post.body,
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> withDatabase(block: (AppDatabase) -> Flow<T>): Flow<T> =
        flow { emit(provider.database()) }.flatMapLatest(block)
}

/** A durable post, free of both the wire format and SQLDelight's generated row type. */
internal data class PostEntity(
    val id: String,
    val title: String,
    val body: String,
    val authorId: Long = 0,
)
